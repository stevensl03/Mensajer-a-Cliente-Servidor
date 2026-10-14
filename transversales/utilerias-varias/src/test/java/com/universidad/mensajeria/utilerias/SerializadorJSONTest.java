package com.universidad.mensajeria.utilerias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializadorJSONTest {

    private final ISerializador json = new SerializadorJSON();

    @Test
    void peticionDeIdaYVuelta() {
        var peticion = new PeticionDTO("7f1", TipoComando.ENVIAR_PRIVADO, Map.of("destino", "160004512"),
                new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null));

        assertEquals(peticion, json.desdeJson(json.aJson(peticion), PeticionDTO.class));
    }

    @Test
    void leeLaPeticionDeLaNotaDelProtocolo() {
        String texto = """
                {"idPeticion":"7f1","comando":"ENVIAR_PRIVADO",
                 "parametros":{"destino":"160004512"},
                 "contenido":{"tipo":"TEXTO","texto":"Hola"}}
                """;
        var peticion = json.desdeJson(texto, PeticionDTO.class);
        assertEquals(TipoComando.ENVIAR_PRIVADO, peticion.comando());
        assertEquals("160004512", peticion.parametro("destino").orElseThrow());
        assertEquals("Hola", peticion.contenido().texto());
    }

    @Test
    void eventoConFechaIsoYDatosJson() {
        JsonNode datos = JsonNodeFactory.instance.objectNode().put("canal", "FORO");
        var evento = new EventoPushDTO(TipoEventoPush.MENSAJE_NUEVO, LocalDateTime.of(2026, 10, 8, 23, 15, 30), datos);

        String texto = json.aJson(evento);

        assertTrue(texto.contains("\"fecha\":\"2026-10-08T23:15:30\""), texto);
        assertEquals(evento, json.desdeJson(texto, EventoPushDTO.class));
    }

    @Test
    void ignoraCamposDesconocidos() {
        var peticion = json.desdeJson("{\"idPeticion\":\"1\",\"comando\":\"LOGIN\",\"extra\":5}", PeticionDTO.class);
        assertEquals(TipoComando.LOGIN, peticion.comando());
        assertTrue(peticion.parametros().isEmpty());
    }

    @Test
    void jsonInvalidoOComandoDesconocido() {
        assertThrows(IllegalArgumentException.class, () -> json.desdeJson("no es json", PeticionDTO.class));
        assertThrows(IllegalArgumentException.class,
                () -> json.desdeJson("{\"idPeticion\":\"1\",\"comando\":\"BORRAR_TODO\"}", PeticionDTO.class));
        assertThrows(IllegalArgumentException.class,
                () -> json.desdeJson("{\"comando\":\"LOGIN\"}", PeticionDTO.class));
    }
}
