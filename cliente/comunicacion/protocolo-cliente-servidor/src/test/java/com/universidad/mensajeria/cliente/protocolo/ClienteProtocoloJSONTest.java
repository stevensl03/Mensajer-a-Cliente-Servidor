package com.universidad.mensajeria.cliente.protocolo;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteProtocoloJSONTest {

    private final ISerializador json = new SerializadorJSON();
    private ConexionFalsa conexion;
    private ClienteProtocoloJSON protocolo;
    private OyenteGrabador oyente;

    @BeforeEach
    void preparar() {
        conexion = new ConexionFalsa();
        protocolo = new ClienteProtocoloJSON(conexion, json, Duration.ofMillis(500));
        oyente = new OyenteGrabador();
        protocolo.suscribir(oyente);
    }

    @Test
    void conectarDevuelveLaIpDeInfoServidorYLaReenviaComoPush() {
        assertEquals("192.168.20.31", protocolo.conectar("127.0.0.1", 5000));

        assertEquals(1, oyente.eventos.size());
        assertEquals(TipoEventoPush.INFO_SERVIDOR, oyente.eventos.get(0).evento());
    }

    @Test
    void sinInfoServidorUsaLaIpDelSocketTrasElTimeout() {
        conexion.alConectar = null;

        assertEquals("10.0.0.5", protocolo.conectar("127.0.0.1", 5000));
    }

    @Test
    void siSeCaeAntesDeInfoServidorConectarFalla() {
        conexion.alConectar = null;
        Thread.ofVirtual().start(() -> {
            dormir(100);
            conexion.caer("El servidor cerró la conexión");
        });

        var e = assertThrows(IllegalStateException.class, () -> protocolo.conectar("127.0.0.1", 5000));

        assertTrue(e.getMessage().contains("El servidor cerró la conexión"), e.getMessage());
    }

    @Test
    void laPeticionLlevaUnIdUnicoYOmiteLosNulos() throws Exception {
        protocolo.conectar("h", 1);

        protocolo.solicitar(TipoComando.ENVIAR_PRIVADO, Map.of("destino", "20201002"),
                new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null));
        protocolo.solicitar(TipoComando.LOGOUT, null, null);

        String primera = conexion.siguienteEnviado();
        PeticionDTO p = json.desdeJson(primera, PeticionDTO.class);
        PeticionDTO q = json.desdeJson(conexion.siguienteEnviado(), PeticionDTO.class);
        assertEquals(TipoComando.ENVIAR_PRIVADO, p.comando());
        assertEquals("20201002", p.parametros().get("destino"));
        assertEquals("Hola", p.contenido().texto());
        assertNotNull(UUID.fromString(p.idPeticion()));
        assertFalse(p.idPeticion().equals(q.idPeticion()));
        assertFalse(primera.contains("datosBase64"), primera);
        assertFalse(json.desdeJson(json.aJson(q), JsonNode.class).has("contenido"));
    }

    @Test
    void correlacionaRespuestasQueLleganFueraDeOrden() throws Exception {
        protocolo.conectar("h", 1);
        CompletableFuture<RespuestaDTO> a = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        CompletableFuture<RespuestaDTO> b = protocolo.solicitar(TipoComando.HISTORIAL, Map.of("conversacion", "FORO"), null);
        String idA = idDe(conexion.siguienteEnviado());
        String idB = idDe(conexion.siguienteEnviado());

        conexion.servidor(respuesta(idB, "{\"mensajes\":[]}"));
        conexion.servidor(respuesta(idA, "{\"usuarios\":[]}"));

        assertTrue(a.get(1, TimeUnit.SECONDS).datos().has("usuarios"));
        assertTrue(b.get(1, TimeUnit.SECONDS).datos().has("mensajes"));
        assertEquals(0, protocolo.pendientes());
    }

    @Test
    void unaRespuestaDeErrorCompletaNormalmente() throws Exception {
        protocolo.conectar("h", 1);
        var f = protocolo.solicitar(TipoComando.LOGIN, Map.of("codigo", "x", "clave", "y"), null);
        String id = idDe(conexion.siguienteEnviado());

        conexion.servidor("{\"idPeticion\":\"" + id + "\",\"exito\":false,\"codigo\":\"CREDENCIALES_INVALIDAS\","
                + "\"mensaje\":\"Código o contraseña incorrectos\"}");

        RespuestaDTO r = f.get(1, TimeUnit.SECONDS);
        assertFalse(r.exito());
        assertEquals(CodigoError.CREDENCIALES_INVALIDAS, r.codigo());
    }

    @Test
    void venceElTimeoutYUnaRespuestaTardiaSeDescarta() throws Exception {
        protocolo.conectar("h", 1);
        var f = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        String id = idDe(conexion.siguienteEnviado());

        var e = assertThrows(ExecutionException.class, () -> f.get(2, TimeUnit.SECONDS));

        assertInstanceOf(TimeoutException.class, e.getCause());
        // La limpieza corre en el hilo del timeout, justo después de completar el futuro.
        for (int i = 0; i < 50 && protocolo.pendientes() > 0; i++) {
            dormir(10);
        }
        assertEquals(0, protocolo.pendientes());
        conexion.servidor(respuesta(id, "{}"));
    }

    @Test
    void unPushNoCompletaPeticionesYLlegaAlOyente() throws Exception {
        protocolo.conectar("h", 1);
        var f = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        conexion.siguienteEnviado();

        conexion.servidor("{\"evento\":\"USUARIO_CONECTADO\",\"fecha\":\"2026-10-25T20:00:00\","
                + "\"datos\":{\"codigo\":\"20201002\",\"nombreCompleto\":\"Luis Gómez\",\"sesiones\":1}}");

        assertFalse(f.isDone());
        EventoPushDTO e = oyente.eventos.get(1);
        assertEquals(TipoEventoPush.USUARIO_CONECTADO, e.evento());
        assertEquals("Luis Gómez", e.datos().get("nombreCompleto").asText());
    }

    @Test
    void descartaRespuestasHuerfanasYTramasIlegibles() throws Exception {
        protocolo.conectar("h", 1);
        var f = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        String id = idDe(conexion.siguienteEnviado());

        conexion.servidor("{\"idPeticion\":null,\"exito\":false,\"codigo\":\"PETICION_INVALIDA\",\"mensaje\":\"JSON mal formado\"}");
        conexion.servidor(respuesta(UUID.randomUUID().toString(), "{}"));
        conexion.servidor("{no es json");
        conexion.servidor("[1,2]");
        conexion.servidor("{\"evento\":\"NO_EXISTE\",\"fecha\":\"2026-10-25T20:00:00\"}");
        assertFalse(f.isDone());

        conexion.servidor(respuesta(id, "{\"usuarios\":[]}"));
        assertTrue(f.get(1, TimeUnit.SECONDS).exito());
        assertEquals(1, oyente.eventos.size());
    }

    @Test
    void unaRespuestaConFormatoInvalidoFallaSoloSuPeticion() throws Exception {
        protocolo.conectar("h", 1);
        var f = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        String id = idDe(conexion.siguienteEnviado());

        // exito=true con un código de error viola el invariante de RespuestaDTO.
        conexion.servidor("{\"idPeticion\":\"" + id + "\",\"exito\":true,\"codigo\":\"ERROR_INTERNO\"}");

        var e = assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void laDesconexionFallaLasPendientesYAvisaAlOyente() throws Exception {
        protocolo.conectar("h", 1);
        var a = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);
        var b = protocolo.solicitar(TipoComando.INFORME, Map.of("tipo", "LOGS"), null);

        conexion.caer("El servidor cerró la conexión");

        for (var f : List.of(a, b)) {
            var e = assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
            assertTrue(e.getCause().getMessage().contains("El servidor cerró la conexión"));
        }
        assertEquals(List.of("El servidor cerró la conexión"), oyente.desconexiones);
        assertEquals(0, protocolo.pendientes());
    }

    @Test
    void sinConexionLaPeticionFallaDeInmediatoSinQuedarPendiente() {
        var f = protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null);

        assertTrue(f.isCompletedExceptionally());
        assertEquals(0, protocolo.pendientes());
    }

    @Test
    void desconectarCierraLaConexionYAvisa() {
        protocolo.conectar("h", 1);

        protocolo.desconectar();

        assertFalse(conexion.estaConectada());
        assertEquals(List.of("Conexión cerrada por el cliente"), oyente.desconexiones);
    }

    @Test
    void unOyenteQueFallaNoImpideAlSiguiente() {
        var otro = new OyenteGrabador();
        protocolo = new ClienteProtocoloJSON(conexion, json, Duration.ofMillis(500));
        protocolo.suscribir(new IOyentePush() {
            @Override
            public void onPush(EventoPushDTO e) {
                throw new IllegalStateException("fallo de prueba");
            }

            @Override
            public void onDesconexion(String motivo) {
                throw new IllegalStateException("fallo de prueba");
            }
        });
        protocolo.suscribir(otro);

        protocolo.conectar("h", 1);
        conexion.caer("x");

        assertEquals(1, otro.eventos.size());
        assertEquals(List.of("x"), otro.desconexiones);
    }

    @Test
    void reconectarRenuevaLaEsperaDeInfoServidor() {
        assertEquals("192.168.20.31", protocolo.conectar("h", 1));
        conexion.caer("caída");
        conexion.alConectar = ConexionFalsa.INFO.replace("192.168.20.31", "192.168.20.99");

        assertEquals("192.168.20.99", protocolo.conectar("h", 1));
    }

    @Test
    void rechazaUnTimeoutNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteProtocoloJSON(conexion, json, Duration.ZERO));
    }

    private String idDe(String peticion) {
        assertNotNull(peticion, "no se envió la petición");
        return json.desdeJson(peticion, PeticionDTO.class).idPeticion();
    }

    private static String respuesta(String id, String datos) {
        return "{\"idPeticion\":\"" + id + "\",\"exito\":true,\"codigo\":\"OK\",\"mensaje\":\"OK\",\"datos\":" + datos + "}";
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Oyente que guarda los eventos y las desconexiones. */
    static final class OyenteGrabador implements IOyentePush {
        final List<EventoPushDTO> eventos = new CopyOnWriteArrayList<>();
        final List<String> desconexiones = new CopyOnWriteArrayList<>();

        @Override
        public void onPush(EventoPushDTO e) {
            eventos.add(e);
        }

        @Override
        public void onDesconexion(String motivo) {
            desconexiones.add(motivo);
        }
    }
}
