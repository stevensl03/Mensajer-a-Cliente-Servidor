package com.universidad.mensajeria.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Evento que el servidor envía al cliente sin que lo pida. Siempre trae
 * {@code evento} y nunca {@code idPeticion}.
 *
 * @param evento tipo del evento.
 * @param fecha  momento en que se generó.
 * @param datos  datos del evento, o {@code null}.
 */
public record EventoPushDTO(TipoEventoPush evento, LocalDateTime fecha, JsonNode datos) {

    public EventoPushDTO {
        Objects.requireNonNull(evento, "evento");
        Objects.requireNonNull(fecha, "fecha");
    }
}
