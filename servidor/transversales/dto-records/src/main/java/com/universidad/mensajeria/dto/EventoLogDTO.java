package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Evento del log del servidor (fila de {@code logs_eventos}).
 *
 * @param id          identificador del evento.
 * @param tipo        tipo del evento.
 * @param descripcion detalle (sin contraseñas ni contenido completo).
 * @param usuario     código del usuario asociado, o {@code null}.
 * @param ip          IP de origen, o {@code null}.
 * @param fecha       fecha y hora.
 */
public record EventoLogDTO(String id, TipoEvento tipo, String descripcion,
                           String usuario, String ip, LocalDateTime fecha) {

    public EventoLogDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(descripcion, "descripcion");
        Objects.requireNonNull(fecha, "fecha");
    }
}
