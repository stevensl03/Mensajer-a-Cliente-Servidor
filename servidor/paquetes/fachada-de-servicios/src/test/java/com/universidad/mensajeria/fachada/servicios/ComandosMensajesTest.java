package com.universidad.mensajeria.fachada.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.servicios.comandos.DespachadorComandos;
import com.universidad.mensajeria.fachada.servicios.comandos.IManejadorComando;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorEnviarForo;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorEnviarPrivado;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorHistorial;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorListarUsuarios;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorObtenerImagen;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ADMIN;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ANA;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.LUIS;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.MARIA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manejadores de envío, historial, imagen y usuarios a través del despachador. */
class ComandosMensajesTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 13, 10, 0);
    private static final ContenidoDTO HOLA = new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null);

    private final GestorConexiones conexiones = new GestorConexiones(10);
    private final GestorMensajesFalso gestor = new GestorMensajesFalso();
    private final AlmacenMensajesFalso almacen = new AlmacenMensajesFalso();
    private final DespachadorComandos despachador;

    private final CanalGrabador ana = abrir(ANA);
    private final CanalGrabador ana2 = abrir(ANA);
    private final CanalGrabador luis = abrir(LUIS);
    private final CanalGrabador maria = abrir(MARIA);

    ComandosMensajesTest() {
        var cs = new ClienteServidorFalso();
        var usuarios = new UsuariosFalso();
        var m = new EnumMap<TipoComando, IManejadorComando>(TipoComando.class);
        m.put(TipoComando.ENVIAR_FORO, new ManejadorEnviarForo(gestor, conexiones, cs));
        m.put(TipoComando.ENVIAR_PRIVADO, new ManejadorEnviarPrivado(gestor, usuarios, conexiones, cs));
        m.put(TipoComando.HISTORIAL, new ManejadorHistorial(almacen, usuarios, conexiones, cs));
        m.put(TipoComando.OBTENER_IMAGEN, new ManejadorObtenerImagen(almacen, conexiones, cs));
        m.put(TipoComando.LISTAR_USUARIOS, new ManejadorListarUsuarios(usuarios, conexiones, cs));
        despachador = new DespachadorComandos(m, conexiones);
    }

    @Test
    void enviarForoEncolaConElRemitenteDeLaSesion() {
        RespuestaDTO r = enviar(ana, peticion("p1", TipoComando.ENVIAR_FORO, Map.of("remitente", LUIS.codigo()), HOLA));

        assertTrue(r.exito());
        SolicitudMensajeDTO s = gestor.encolados.get(0);
        assertEquals(ANA.codigo(), s.remitente(), "el remitente sale de la sesión, nunca del JSON");
        assertEquals(Canal.FORO, s.canal());
        assertNull(s.destinatario());
        assertEquals("p1", s.idPeticion());
        assertEquals(ana.getId(), s.idSesion());
        assertEquals(HOLA, s.contenido());
        assertEquals(s.idMensaje(), r.datos().get("idMensaje").asText());
        assertEquals("ENCOLADO", r.datos().get("estado").asText());
    }

    @Test
    void conLaColaLlenaRespondeColaLlena() {
        gestor.llena = true;

        assertEquals(CodigoError.COLA_LLENA, enviar(ana, peticion("p1", TipoComando.ENVIAR_FORO, null, HOLA)).codigo());
    }

    @Test
    void enviarSinContenidoEsContenidoInvalido() {
        assertEquals(CodigoError.CONTENIDO_INVALIDO,
                enviar(ana, peticion("p1", TipoComando.ENVIAR_FORO, null, null)).codigo());
    }

    @Test
    void enviarPrivadoEncolaConElDestino() {
        RespuestaDTO r = enviar(ana, privado(LUIS.codigo()));

        assertTrue(r.exito());
        assertEquals(Canal.PRIVADO, gestor.encolados.get(0).canal());
        assertEquals(LUIS.codigo(), gestor.encolados.get(0).destinatario());
    }

    @Test
    void privadoAlAdminEstaPermitido() {
        assertTrue(enviar(ana, privado(ADMIN.codigo())).exito());
    }

    @Test
    void privadoAUnDestinoInexistenteOASiMismoSeRechaza() {
        assertEquals(CodigoError.DESTINO_INEXISTENTE, enviar(ana, privado("99999999")).codigo());
        assertEquals(CodigoError.DESTINO_INEXISTENTE, enviar(ana, privado(ANA.codigo())).codigo());
        assertTrue(gestor.encolados.isEmpty());
    }

    @Test
    void historialDelForoDevuelveLosUltimosOrdenados() {
        for (int i = 1; i <= 5; i++) {
            almacen.sembrar(texto("f" + i, Canal.FORO, ANA.codigo(), null, 10 - i), null);
        }

        RespuestaDTO r = enviar(luis, peticion("p1", TipoComando.HISTORIAL, Map.of("conversacion", "FORO", "limite", "3"), null));

        assertEquals(List.of("f3", "f2", "f1"), ids(r.datos().get("mensajes")));
    }

    @Test
    void historialPrivadoSoloTraeLaConversacionConEseUsuario() {
        almacen.sembrar(texto("p1", Canal.PRIVADO, ANA.codigo(), LUIS.codigo(), 1), null);
        almacen.sembrar(texto("p2", Canal.PRIVADO, LUIS.codigo(), ANA.codigo(), 2), null);
        almacen.sembrar(texto("p3", Canal.PRIVADO, MARIA.codigo(), ANA.codigo(), 3), null);

        RespuestaDTO r = enviar(ana, peticion("h", TipoComando.HISTORIAL, Map.of("conversacion", LUIS.codigo()), null));

        assertEquals(List.of("p1", "p2"), ids(r.datos().get("mensajes")));
    }

    @Test
    void historialConUsuarioInexistenteOLimiteInvalido() {
        assertEquals(CodigoError.DESTINO_INEXISTENTE,
                enviar(ana, peticion("h", TipoComando.HISTORIAL, Map.of("conversacion", "99999999"), null)).codigo());
        assertEquals(CodigoError.PETICION_INVALIDA,
                enviar(ana, peticion("h", TipoComando.HISTORIAL, Map.of("conversacion", "FORO", "limite", "0"), null)).codigo());
    }

    @Test
    void obtenerImagenDelForoDevuelveLosBytesEnBase64() {
        byte[] original = {9, 8, 7};
        almacen.sembrar(imagen("i1", Canal.FORO, ANA.codigo(), null), original);

        RespuestaDTO r = enviar(luis, obtener("i1", "ORIGINAL"));

        assertTrue(r.exito());
        assertEquals("i1", r.datos().get("idMensaje").asText());
        assertEquals("ORIGINAL", r.datos().get("etapa").asText());
        assertEquals("foto.png", r.datos().get("nombreArchivo").asText());
        assertEquals(Base64.getEncoder().encodeToString(original), r.datos().get("datosBase64").asText());
    }

    @Test
    void unaImagenPrivadaAjenaNoSeEncuentra() {
        almacen.sembrar(imagen("i2", Canal.PRIVADO, ANA.codigo(), LUIS.codigo()), new byte[]{1});

        assertTrue(enviar(luis, obtener("i2", "ORIGINAL")).exito());
        assertTrue(enviar(ana2, obtener("i2", "ORIGINAL")).exito());
        assertEquals(CodigoError.NO_ENCONTRADO, enviar(maria, obtener("i2", "ORIGINAL")).codigo());
    }

    @Test
    void imagenInexistenteEtapaSinArchivoOEtapaInvalida() {
        almacen.sembrar(imagen("i3", Canal.FORO, ANA.codigo(), null), new byte[]{1});
        almacen.sembrar(texto("t1", Canal.FORO, ANA.codigo(), null, 0), null);

        assertEquals(CodigoError.NO_ENCONTRADO, enviar(ana, obtener("nada", "ORIGINAL")).codigo());
        assertEquals(CodigoError.NO_ENCONTRADO, enviar(ana, obtener("i3", "SEPIA")).codigo());
        assertEquals(CodigoError.NO_ENCONTRADO, enviar(ana, obtener("t1", "ORIGINAL")).codigo());
        assertEquals(CodigoError.PETICION_INVALIDA, enviar(ana, obtener("i3", "ACUARELA")).codigo());
    }

    @Test
    void listarUsuariosMuestraATodosConSusSesionesYAlAdminConectado() {
        RespuestaDTO r = enviar(luis, peticion("p1", TipoComando.LISTAR_USUARIOS, null, null));

        Map<String, JsonNode> porCodigo = new HashMap<>();
        r.datos().get("usuarios").forEach(u -> porCodigo.put(u.get("usuario").get("codigo").asText(), u));
        assertEquals(4, porCodigo.size());
        assertTrue(porCodigo.get(ADMIN.codigo()).get("conectado").asBoolean());
        assertEquals(2, porCodigo.get(ANA.codigo()).get("sesiones").asInt());
        assertTrue(porCodigo.get(LUIS.codigo()).get("conectado").asBoolean());

        conexiones.cerrarSesion(maria.getId());
        r = enviar(luis, peticion("p2", TipoComando.LISTAR_USUARIOS, null, null));
        r.datos().get("usuarios").forEach(u -> porCodigo.put(u.get("usuario").get("codigo").asText(), u));
        assertTrue(!porCodigo.get(MARIA.codigo()).get("conectado").asBoolean());
        assertEquals(0, porCodigo.get(MARIA.codigo()).get("sesiones").asInt());
    }

    private RespuestaDTO enviar(CanalGrabador c, PeticionDTO p) {
        return despachador.despachar(p, c);
    }

    private CanalGrabador abrir(UsuarioDTO u) {
        var c = new CanalGrabador();
        conexiones.abrirSesion(c, u);
        return c;
    }

    private static PeticionDTO peticion(String id, TipoComando comando, Map<String, String> parametros,
                                        ContenidoDTO contenido) {
        return new PeticionDTO(id, comando, parametros, contenido);
    }

    private static PeticionDTO privado(String destino) {
        return peticion("p1", TipoComando.ENVIAR_PRIVADO, Map.of("destino", destino), HOLA);
    }

    private static PeticionDTO obtener(String id, String etapa) {
        return peticion("img", TipoComando.OBTENER_IMAGEN, Map.of("idMensaje", id, "etapa", etapa), null);
    }

    private static MensajeDTO texto(String id, Canal canal, String de, String para, int minuto) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, canal, de, para, T0.plusMinutes(minuto), "h", "Hola", 4, 1,
                null, List.of());
    }

    private static MensajeDTO imagen(String id, Canal canal, String de, String para) {
        var original = new ImagenVersionDTO(0, TipoFiltro.ORIGINAL, "foto.png", "/almacen/" + id, 3, "h0");
        return new MensajeDTO(id, TipoMensaje.IMAGEN, canal, de, para, T0, "h", null, 0, 0, "foto.png",
                List.of(original));
    }

    private static List<String> ids(JsonNode mensajes) {
        var ids = new ArrayList<String>();
        mensajes.forEach(m -> ids.add(m.get("id").asText()));
        return ids;
    }
}
