package com.universidad.mensajeria.cliente.fachada;

import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FachadaClienteTest {

    private static final SerializadorJSON JSON = new SerializadorJSON();
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final MensajeDTO FORO = new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.FORO, "20201002", null,
            LocalDateTime.of(2026, 10, 25, 20, 0), "h", "Hola", 4, 1, null, List.of());

    private Dobles.Usuarios usuarios;
    private Dobles.Chat chat;
    private Dobles.Informes informes;
    private Dobles.Protocolo protocolo;
    private Dobles.Observador observador;
    private FachadaCliente fachada;

    @BeforeEach
    void preparar() {
        usuarios = new Dobles.Usuarios();
        usuarios.respuestaLogin = CompletableFuture.completedFuture(ANA);
        chat = new Dobles.Chat();
        informes = new Dobles.Informes();
        protocolo = new Dobles.Protocolo();
        fachada = new FachadaCliente(usuarios, chat, informes, protocolo, JSON);
        protocolo.suscribir(fachada);
        observador = new Dobles.Observador();
        fachada.suscribir(observador);
    }

    @Test
    void conectarReutilizaLaConexionConElMismoServidor() {
        assertEquals("192.168.20.31", fachada.conectar("127.0.0.1", 5000));
        fachada.conectar("127.0.0.1", 5000);
        assertEquals(1, protocolo.conexiones.get());

        fachada.conectar("10.0.0.2", 5000);

        assertEquals(2, protocolo.conexiones.get());
        assertEquals(1, protocolo.desconexiones.get());
        assertEquals("192.168.20.31", fachada.getIpServidor());
        assertTrue(observador.desconexiones.isEmpty(), "sin sesión no se avisa desconexión");
    }

    @Test
    void loginUsaElPuertoDeConectarEIniciaElChat() throws Exception {
        fachada.conectar("127.0.0.1", 5001);

        assertEquals(ANA, fachada.login("20201001", "clave123").get(5, TimeUnit.SECONDS));

        assertEquals(List.of("login 20201001 5001"), usuarios.llamadas);
        assertEquals(List.of("iniciarSesion 20201001 7"), chat.llamadas);
        assertEquals(ANA, fachada.usuarioActual().orElseThrow());
        assertEquals(7, fachada.sesionLocal());
        assertEquals(42, fachada.mensajesGuardados());
    }

    @Test
    void loginSinConectarFalla() {
        var e = assertThrows(ExecutionException.class, () -> fachada.login("x", "y").get(5, TimeUnit.SECONDS));

        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertTrue(usuarios.llamadas.isEmpty());
    }

    @Test
    void unMensajeNuevoSeGuardaSiempreYSoloSeAvisaConSesion() throws Exception {
        fachada.onPush(push("MENSAJE_NUEVO", "{\"mensaje\":" + JSON.aJson(FORO) + "}"));
        assertEquals(List.of(FORO), chat.recibidos);
        assertTrue(observador.mensajes.isEmpty(), "antes del login solo se guarda (R-30)");

        iniciarSesion();
        fachada.onPush(push("MENSAJE_NUEVO", "{\"mensaje\":" + JSON.aJson(FORO) + "}"));

        assertEquals(2, chat.recibidos.size());
        assertEquals(List.of(FORO), observador.mensajes);
    }

    @Test
    void losCambiosDeUsuariosPidenLaListaSoloConSesion() throws Exception {
        var lista = List.of(new EstadoUsuarioDTO(ANA, true, 1));
        usuarios.lista = lista;
        fachada.onPush(push("USUARIO_CONECTADO", "{\"codigo\":\"20201001\",\"nombreCompleto\":\"Ana Pérez\",\"sesiones\":1}"));
        assertTrue(usuarios.llamadas.isEmpty(), "el push propio llega antes de la respuesta de LOGIN (R-30)");

        iniciarSesion();
        fachada.onPush(push("USUARIO_DESCONECTADO", "{\"codigo\":\"20201002\",\"sesiones\":0}"));

        assertTrue(usuarios.llamadas.contains("listar"));
        assertEquals(List.of(lista), observador.usuarios);
    }

    @Test
    void sesionCerradaYLaCaidaQueLaSigueAvisanUnaSolaVez() throws Exception {
        iniciarSesion();

        fachada.onPush(push("SESION_CERRADA", "{\"motivo\":\"CERRADA_POR_ADMIN\"}"));
        protocolo.oyente.onDesconexion("El servidor cerró la conexión");

        esperar(() -> !observador.desconexiones.isEmpty());
        Thread.sleep(100);
        assertEquals(List.of("CERRADA_POR_ADMIN"), observador.desconexiones);
        assertTrue(usuarios.llamadas.contains("logout"));
        assertTrue(chat.llamadas.contains("cerrarSesion"));
        assertTrue(fachada.usuarioActual().isEmpty());
        assertEquals(-1, fachada.sesionLocal());
    }

    @Test
    void unaCaidaDeRedConSesionAvisaConSuMotivo() throws Exception {
        iniciarSesion();

        fachada.onDesconexion("Error de red: Connection reset");

        esperar(() -> !observador.desconexiones.isEmpty());
        assertEquals(List.of("Error de red: Connection reset"), observador.desconexiones);
        assertTrue(chat.llamadas.contains("cerrarSesion"));
    }

    @Test
    void logoutNoSeAvisaComoDesconexionYPermiteVolverAEntrar() throws Exception {
        iniciarSesion();

        fachada.logout();
        fachada.logout();

        Thread.sleep(100);
        assertTrue(observador.desconexiones.isEmpty(), "el logout pedido no es una caída (R-31)");
        assertEquals(1, usuarios.llamadas.stream().filter("logout"::equals).count());
        assertEquals(1, protocolo.desconexiones.get());
        assertTrue(fachada.usuarioActual().isEmpty());

        fachada.conectar("127.0.0.1", 5000);
        fachada.login("20201001", "clave123").get(5, TimeUnit.SECONDS);
        assertEquals(2, protocolo.conexiones.get(), "tras el logout se reconecta (D-06)");
    }

    @Test
    void logoutSinSesionSoloCierraLaConexion() {
        fachada.conectar("127.0.0.1", 5000);

        fachada.logout();

        assertEquals(1, protocolo.desconexiones.get());
        assertTrue(usuarios.llamadas.isEmpty());
    }

    @Test
    void historialDevuelveLoLocalYAvisaLoQueLlegaDelServidor() throws Exception {
        iniciarSesion();
        chat.locales = List.of(FORO);

        assertEquals(List.of(FORO), fachada.historial("FORO"));
        assertTrue(observador.mensajes.isEmpty());
        var nuevo = new MensajeDTO("m0", TipoMensaje.TEXTO, Canal.FORO, "20201003", null,
                LocalDateTime.of(2026, 10, 25, 19, 0), "h", "antes", 5, 1, null, List.of());
        chat.sincronizacion.complete(List.of(nuevo));

        assertEquals(List.of(nuevo), observador.mensajes);
        assertTrue(chat.llamadas.containsAll(List.of("historial FORO", "sincronizar FORO")));
    }

    @Test
    void delegaEnvioImagenEInformes() throws Exception {
        var hola = new ContenidoDTO(TipoMensaje.TEXTO, "hola", null, null);

        fachada.enviarForo(hola).get(5, TimeUnit.SECONDS);
        fachada.enviarPrivado("20201002", hola).get(5, TimeUnit.SECONDS);
        fachada.imagen("img", TipoFiltro.SEPIA).get(5, TimeUnit.SECONDS);
        fachada.informe(TipoInforme.LOGS, Map.of("tipoEvento", "ERROR")).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("enviar FORO null hola", "enviar PRIVADO 20201002 hola", "imagen img SEPIA"), chat.llamadas);
        assertEquals(List.of("informe LOGS {tipoEvento=ERROR}"), informes.llamadas);
    }

    @Test
    void unObservadorQueFallaNoImpideAvisarAlSiguiente() throws Exception {
        var segundo = new Dobles.Observador();
        fachada = new FachadaCliente(usuarios, chat, informes, protocolo, JSON);
        protocolo.suscribir(fachada);
        fachada.suscribir(new Dobles.Observador() {
            @Override
            public void onMensaje(MensajeDTO m) {
                throw new IllegalStateException("fallo de prueba");
            }
        });
        fachada.suscribir(segundo);
        iniciarSesion();

        var e = assertThrows(IllegalStateException.class,
                () -> fachada.onPush(push("MENSAJE_NUEVO", "{\"mensaje\":" + JSON.aJson(FORO) + "}")));

        assertEquals("fallo de prueba", e.getMessage());
        assertEquals(List.of(FORO), segundo.mensajes);
    }

    @Test
    void ignoraInfoServidorYCupoLleno() {
        fachada.onPush(push("INFO_SERVIDOR", "{\"ipServidor\":\"x\"}"));
        fachada.onPush(push("CUPO_LLENO", null));

        assertTrue(chat.recibidos.isEmpty());
        assertTrue(observador.desconexiones.isEmpty());
    }

    private void iniciarSesion() throws Exception {
        fachada.conectar("127.0.0.1", 5000);
        fachada.login("20201001", "clave123").get(5, TimeUnit.SECONDS);
        usuarios.llamadas.clear();
        chat.llamadas.clear();
    }

    private static EventoPushDTO push(String evento, String datos) {
        String texto = "{\"evento\":\"" + evento + "\",\"fecha\":\"2026-10-25T20:00:00\""
                + (datos == null ? "" : ",\"datos\":" + datos) + "}";
        return JSON.desdeJson(texto, EventoPushDTO.class);
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        for (int i = 0; i < 200 && !condicion.getAsBoolean(); i++) {
            Thread.sleep(10);
        }
        assertTrue(condicion.getAsBoolean(), "la condición no se cumplió a tiempo");
    }
}
