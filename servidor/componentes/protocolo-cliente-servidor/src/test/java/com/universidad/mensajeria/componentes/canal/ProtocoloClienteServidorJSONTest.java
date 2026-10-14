package com.universidad.mensajeria.componentes.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocoloClienteServidorJSONTest {

    private final SerializadorJSON json = new SerializadorJSON();
    private final IClienteServidor protocolo =
            new ProtocoloClienteServidorJSON(json, new ValidadorPeticiones(4000, 1024));

    @Test
    void interpretaElEjemploDelProtocolo() {
        PeticionDTO p = protocolo.interpretar("""
                {"idPeticion":"7f1c","comando":"ENVIAR_PRIVADO","parametros":{"destino":"20201002"},
                 "contenido":{"tipo":"TEXTO","texto":"Hola"}}""");

        assertEquals("7f1c", p.idPeticion());
        assertEquals(TipoComando.ENVIAR_PRIVADO, p.comando());
        assertEquals("20201002", p.parametro("destino").orElseThrow());
        assertEquals(TipoMensaje.TEXTO, p.contenido().tipo());
        assertEquals("Hola", p.contenido().texto());
    }

    @Test
    void ignoraCamposDesconocidosYAceptaNumerosEnParametros() {
        PeticionDTO p = protocolo.interpretar(
                "{\"idPeticion\":\"1\",\"comando\":\"HISTORIAL\",\"parametros\":{\"conversacion\":\"FORO\",\"limite\":50},\"x\":true}");
        assertEquals("50", p.parametro("limite").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no es json", "[1,2]", "\"texto\"", "{\"comando\":\"LOGIN\"", "null"})
    void jsonInvalidoSinIdPeticion(String texto) {
        var e = assertThrows(PeticionInvalidaException.class, () -> protocolo.interpretar(texto));
        assertEquals(CodigoError.PETICION_INVALIDA, e.getCodigo());
        assertNull(e.getIdPeticion());
    }

    @Test
    void faltaIdPeticion() {
        var e = assertThrows(PeticionInvalidaException.class,
                () -> protocolo.interpretar("{\"comando\":\"LOGOUT\"}"));
        assertNull(e.getIdPeticion());
        assertThrows(PeticionInvalidaException.class,
                () -> protocolo.interpretar("{\"idPeticion\":5,\"comando\":\"LOGOUT\"}"), "idPeticion debe ser texto");
    }

    @Test
    void comandoDesconocidoOFaltanteConservaElIdPeticion() {
        for (String texto : new String[]{
                "{\"idPeticion\":\"9\",\"comando\":\"BORRAR_TODO\"}",
                "{\"idPeticion\":\"9\"}",
                "{\"idPeticion\":\"9\",\"comando\":\"ENVIAR_FORO\",\"contenido\":{\"tipo\":\"VIDEO\"}}",
                "{\"idPeticion\":\"9\",\"comando\":\"LOGIN\",\"parametros\":[1]}"}) {
            var e = assertThrows(PeticionInvalidaException.class, () -> protocolo.interpretar(texto), texto);
            assertEquals("9", e.getIdPeticion(), texto);
            assertEquals(CodigoError.PETICION_INVALIDA, e.getCodigo(), texto);
        }
    }

    @Test
    void aplicaElValidador() {
        var e = assertThrows(PeticionInvalidaException.class,
                () -> protocolo.interpretar("{\"idPeticion\":\"3\",\"comando\":\"LOGIN\",\"parametros\":{\"codigo\":\"1\"}}"));
        assertEquals("3", e.getIdPeticion());
        assertEquals(CodigoError.PETICION_INVALIDA, e.getCodigo());

        var c = assertThrows(PeticionInvalidaException.class, () -> protocolo.interpretar(
                "{\"idPeticion\":\"4\",\"comando\":\"ENVIAR_FORO\",\"contenido\":{\"tipo\":\"TEXTO\",\"texto\":\" \"}}"));
        assertEquals(CodigoError.CONTENIDO_INVALIDO, c.getCodigo());
    }

    @Test
    void respuestaSegunElEjemplo() {
        RespuestaDTO r = protocolo.exito("7f1c", Map.of("idMensaje", "m1"));

        assertEquals("{\"idPeticion\":\"7f1c\",\"exito\":true,\"codigo\":\"OK\",\"mensaje\":\"OK\","
                + "\"datos\":{\"idMensaje\":\"m1\"}}", protocolo.serializar(r));
    }

    @Test
    void errorSinDatosYSinNulos() {
        String texto = protocolo.serializar(protocolo.error("7f1c", CodigoError.COLA_LLENA, "Cola llena"));

        assertEquals("{\"idPeticion\":\"7f1c\",\"exito\":false,\"codigo\":\"COLA_LLENA\",\"mensaje\":\"Cola llena\"}", texto);
        assertThrows(IllegalArgumentException.class, () -> protocolo.error("1", CodigoError.OK, "x"));
    }

    @Test
    void laRespuestaSiempreTraeIdPeticionAunqueSeaNulo() {
        JsonNode r = json.desdeJson(
                protocolo.serializar(protocolo.error(null, CodigoError.PETICION_INVALIDA, "JSON mal formado")),
                JsonNode.class);

        assertTrue(r.has("idPeticion"));
        assertTrue(r.get("idPeticion").isNull());
        assertFalse(r.has("evento"));
    }

    @Test
    void eventoSegunElEjemploSinIdPeticion() {
        var evento = new EventoPushDTO(TipoEventoPush.USUARIO_DESCONECTADO,
                LocalDateTime.of(2026, 10, 13, 10, 42, 5, 118_000_000),
                json.aArbol(Map.of("codigo", "20201001")));

        String texto = protocolo.serializar(evento);

        assertEquals("{\"evento\":\"USUARIO_DESCONECTADO\",\"fecha\":\"2026-10-13T10:42:05.118\","
                + "\"datos\":{\"codigo\":\"20201001\"}}", texto);
    }

    @Test
    void exitoSinDatos() {
        assertNull(protocolo.exito("1", null).datos());
        assertEquals("{\"idPeticion\":\"1\",\"exito\":true,\"codigo\":\"OK\",\"mensaje\":\"OK\"}",
                protocolo.serializar(protocolo.exito("1", null)));
    }
}
