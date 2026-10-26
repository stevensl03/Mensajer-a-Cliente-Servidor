package com.universidad.mensajeria.cliente.servicios.chat;

import com.universidad.mensajeria.cliente.persistencia.FabricaRepositoriosH2;
import com.universidad.mensajeria.cliente.persistencia.IRepositorioMensajesLocal;
import com.universidad.mensajeria.cliente.persistencia.ProveedorConexionH2;
import com.universidad.mensajeria.cliente.servicios.ProtocoloFalso;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioChatTest {

    private static final String HASH = "b".repeat(64);
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 25, 20, 0, 0);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private ProtocoloFalso protocolo;
    private ProveedorConexionH2 proveedor;
    private IRepositorioMensajesLocal repo;
    private ServicioChat chat;

    @BeforeEach
    void preparar() {
        protocolo = new ProtocoloFalso();
        proveedor = new ProveedorConexionH2("jdbc:h2:mem:" + UUID.randomUUID() + "_{codigo}", "sa", "");
        proveedor.abrir(ANA.codigo());
        var repos = new FabricaRepositoriosH2(proveedor);
        repo = repos.mensajes();
        long sesion = repos.sesiones().abrir(ANA, "192.168.20.31", 5000);
        chat = new ServicioChat(protocolo, repo, new CacheMensajesSesion(10), new SerializadorJSON());
        chat.iniciarSesion(ANA, sesion);
    }

    @AfterEach
    void cerrar() {
        proveedor.cerrar();
    }

    @Test
    void enviaAlForoYPorPrivadoConElComandoCorrecto() throws Exception {
        protocolo.responder(TipoComando.ENVIAR_FORO, encolado("m1"));
        protocolo.responder(TipoComando.ENVIAR_PRIVADO, encolado("m2"));

        chat.enviar(Canal.FORO, null, texto("Hola foro")).get(5, TimeUnit.SECONDS);
        chat.enviar(Canal.PRIVADO, "20201002", texto("Hola Luis")).get(5, TimeUnit.SECONDS);

        var foro = protocolo.siguiente();
        var privado = protocolo.siguiente();
        assertEquals(TipoComando.ENVIAR_FORO, foro.comando());
        assertTrue(foro.params().isEmpty());
        assertEquals("Hola foro", foro.contenido().texto());
        assertEquals(TipoComando.ENVIAR_PRIVADO, privado.comando());
        assertEquals(Map.of("destino", "20201002"), privado.params());
        assertEquals(0, repo.contar(), "ENCOLADO no pinta ni guarda el mensaje (R-33)");
    }

    @Test
    void validaElContenidoComoElServidorSinEnviarNada() {
        String emojis = "😀".repeat(4000);
        protocolo.responder(TipoComando.ENVIAR_FORO, encolado("ok"));

        assertDoesNotFail(() -> chat.enviar(Canal.FORO, null, texto(emojis)).get(5, TimeUnit.SECONDS));
        protocolo.solicitudes.clear();
        for (var c : List.of(
                texto("   "),
                texto("a".repeat(4001)),
                imagen("foto.webp", PNG),
                imagen("../foto.png", PNG),
                imagen("foto.png", new byte[5_242_881]),
                new ContenidoDTO(TipoMensaje.IMAGEN, null, "foto.png", "no es base64!"))) {
            var e = assertThrows(ExecutionException.class, () -> chat.enviar(Canal.FORO, null, c).get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
        }
        var sinDestino = assertThrows(ExecutionException.class,
                () -> chat.enviar(Canal.PRIVADO, " ", texto("x")).get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, sinDestino.getCause());
        assertTrue(protocolo.solicitudes.isEmpty(), "nada inválido llega al servidor");
    }

    @Test
    void unRechazoDelServidorLlegaConSuCodigo() {
        protocolo.responder(TipoComando.ENVIAR_PRIVADO, ProtocoloFalso.error(CodigoError.DESTINO_INEXISTENTE, "no existe"));

        var e = assertThrows(ExecutionException.class,
                () -> chat.enviar(Canal.PRIVADO, "99999999", texto("x")).get(5, TimeUnit.SECONDS));

        assertEquals(CodigoError.DESTINO_INEXISTENTE, assertInstanceOf(ExcepcionServidor.class, e.getCause()).codigo());
    }

    @Test
    void recibirGuardaUnaVezYMarcaLosPropios() throws Exception {
        var mio = mensaje("m1", Canal.FORO, ANA.codigo(), null, T0);
        var ajeno = mensaje("m2", Canal.FORO, "20201002", null, T0.plusSeconds(1));

        chat.recibir(mio);
        chat.recibir(mio);
        chat.recibir(ajeno);

        assertEquals(2, repo.contar());
        assertEquals(2, chat.mensajesGuardados());
        assertEquals(List.of("m1", "m2"), ids(chat.historial("FORO")));
    }

    @Test
    void losMensajesQueLleganAntesDeLaSesionSeGuardanAlIniciarla() {
        chat.cerrarSesion();
        chat.recibir(mensaje("m1", Canal.FORO, "20201002", null, T0));
        assertEquals(0, repo.contar());

        chat.iniciarSesion(ANA, 1);

        assertEquals(1, repo.contar());
    }

    @Test
    void historialLeeH2LaPrimeraVezYLuegoLaCacheConLoNuevo() {
        repo.guardar(mensaje("viejo", Canal.PRIVADO, "20201002", ANA.codigo(), T0), 1, false);

        assertEquals(List.of("viejo"), ids(chat.historial("20201002")));
        chat.recibir(mensaje("nuevo", Canal.PRIVADO, ANA.codigo(), "20201002", T0.plusSeconds(5)));
        chat.recibir(mensaje("foro", Canal.FORO, "20201003", null, T0));

        assertEquals(List.of("viejo", "nuevo"), ids(chat.historial("20201002")));
        assertEquals(List.of("foro"), ids(chat.historial("FORO")));
    }

    @Test
    void sincronizarGuardaYDevuelveSoloLoQueFaltaba() throws Exception {
        chat.recibir(mensaje("m1", Canal.FORO, "20201002", null, T0));
        protocolo.responder(TipoComando.HISTORIAL, ProtocoloFalso.exito("{\"mensajes\":["
                + json(mensaje("m0", Canal.FORO, "20201003", null, T0.minusMinutes(1))) + ","
                + json(mensaje("m1", Canal.FORO, "20201002", null, T0)) + "]}"));

        List<MensajeDTO> nuevos = chat.sincronizar("FORO").get(5, TimeUnit.SECONDS);

        assertEquals(List.of("m0"), ids(nuevos));
        assertEquals(Map.of("conversacion", "FORO"), protocolo.siguiente().params());
        assertEquals(List.of("m0", "m1"), ids(chat.historial("FORO")));
        assertEquals(2, repo.contar());
    }

    @Test
    void unaImagenSeDescargaUnaVezYLuegoSaleDeH2() throws Exception {
        chat.recibir(mensaje("img", Canal.FORO, "20201002", null, T0));
        protocolo.responder(TipoComando.OBTENER_IMAGEN, ProtocoloFalso.exito(
                "{\"idMensaje\":\"img\",\"etapa\":\"SEPIA\",\"nombreArchivo\":\"f_sepia.png\",\"datosBase64\":\""
                        + Base64.getEncoder().encodeToString(PNG) + "\"}"));

        assertArrayEquals(PNG, chat.imagen("img", TipoFiltro.SEPIA).get(5, TimeUnit.SECONDS));
        assertArrayEquals(PNG, chat.imagen("img", TipoFiltro.SEPIA).get(5, TimeUnit.SECONDS));

        var pedida = protocolo.siguiente();
        assertEquals(Map.of("idMensaje", "img", "etapa", "SEPIA"), pedida.params());
        assertTrue(protocolo.solicitudes.isEmpty(), "la segunda vez sale de H2");
        assertArrayEquals(PNG, repo.adjunto("img", TipoFiltro.SEPIA).orElseThrow());
    }

    @Test
    void elOriginalEnviadoSeGuardaSiLaRespuestaLlegaAntesQueElPush() throws Exception {
        protocolo.responder(TipoComando.ENVIAR_FORO, encolado("img"));

        chat.enviar(Canal.FORO, null, imagen("foto.png", PNG)).get(5, TimeUnit.SECONDS);
        assertFalse(repo.adjunto("img", TipoFiltro.ORIGINAL).isPresent());
        chat.recibir(mensaje("img", Canal.FORO, ANA.codigo(), null, T0));

        assertArrayEquals(PNG, repo.adjunto("img", TipoFiltro.ORIGINAL).orElseThrow());
    }

    @Test
    void elOriginalEnviadoSeGuardaSiElPushLlegaAntesQueLaRespuesta() throws Exception {
        var f = chat.enviar(Canal.FORO, null, imagen("foto.png", PNG));
        var envio = protocolo.siguiente();
        chat.recibir(mensaje("img", Canal.FORO, ANA.codigo(), null, T0));

        envio.futuro().complete(encolado("img"));
        f.get(5, TimeUnit.SECONDS);

        assertArrayEquals(PNG, repo.adjunto("img", TipoFiltro.ORIGINAL).orElseThrow());
    }

    @Test
    void sinSesionNoHayHistorialPrivadoYTrasCerrarNoSeEscribe() {
        chat.cerrarSesion();

        assertThrows(IllegalStateException.class, () -> chat.historial("20201002"));
        assertEquals(0, chat.mensajesGuardados());
        chat.recibir(mensaje("m1", Canal.FORO, "20201002", null, T0));
        assertEquals(0, repo.contar());
    }

    private static void assertDoesNotFail(org.junit.jupiter.api.function.Executable e) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(e);
    }

    private static ContenidoDTO texto(String t) {
        return new ContenidoDTO(TipoMensaje.TEXTO, t, null, null);
    }

    private static ContenidoDTO imagen(String nombre, byte[] datos) {
        return new ContenidoDTO(TipoMensaje.IMAGEN, null, nombre, Base64.getEncoder().encodeToString(datos));
    }

    private static com.universidad.mensajeria.dto.RespuestaDTO encolado(String id) {
        return ProtocoloFalso.exito("{\"idMensaje\":\"" + id + "\",\"estado\":\"ENCOLADO\"}");
    }

    private static MensajeDTO mensaje(String id, Canal canal, String remitente, String destinatario, LocalDateTime f) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, canal, remitente, destinatario, f, HASH, "texto " + id,
                7, 2, null, List.of());
    }

    private static String json(MensajeDTO m) {
        return new SerializadorJSON().aJson(m);
    }

    private static List<String> ids(List<MensajeDTO> l) {
        assertNull(l.stream().filter(m -> m.id() == null).findAny().orElse(null));
        return l.stream().map(MensajeDTO::id).toList();
    }
}
