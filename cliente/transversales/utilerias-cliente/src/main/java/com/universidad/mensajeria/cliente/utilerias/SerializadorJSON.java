package com.universidad.mensajeria.cliente.utilerias;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * {@link ISerializador} sobre Jackson, con la misma configuración que el
 * servidor: fechas en ISO-8601 sin zona, campos nulos omitidos y campos
 * desconocidos ignorados. {@link ObjectMapper} es thread-safe una vez
 * configurado.
 */
public final class SerializadorJSON implements ISerializador {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public String aJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudo serializar a JSON: " + e.getOriginalMessage(), e);
        }
    }

    @Override
    public <T> T desdeJson(String s, Class<T> tipo) {
        Objects.requireNonNull(s, "s");
        Objects.requireNonNull(tipo, "tipo");
        try {
            return mapper.readValue(s, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON inválido para " + tipo.getSimpleName() + ": "
                    + e.getOriginalMessage(), e);
        }
    }

    @Override
    public <T> T convertir(JsonNode n, TypeReference<T> tipo) {
        Objects.requireNonNull(n, "n");
        Objects.requireNonNull(tipo, "tipo");
        try {
            return mapper.treeToValue(n, mapper.constructType(tipo));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON inválido para " + tipo.getType().getTypeName() + ": "
                    + e.getMessage(), e);
        }
    }
}
