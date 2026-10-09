package com.universidad.mensajeria.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Evento que el servidor envía al cliente sin que lo pida. El cliente lo
 * distingue de una {@link RespuestaDTO} porque trae el campo {@code tipo}.
 *
 * @param tipo  tipo del evento.
 * @param datos datos del evento, o {@code null}.
 * @param fecha momento en que se generó.
 */
public record EventoPushDTO(TipoEventoPush tipo, JsonNode datos, LocalDateTime fecha) {

    public EventoPushDTO {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(fecha, "fecha");
    }

    /** Crea un evento con la fecha actual. */
    public static EventoPushDTO de(TipoEventoPush tipo, JsonNode datos) {
        return new EventoPushDTO(tipo, datos, LocalDateTime.now());
    }
}
