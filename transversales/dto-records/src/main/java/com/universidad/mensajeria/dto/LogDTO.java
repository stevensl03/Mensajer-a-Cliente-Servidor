package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;

/**
 * Evento de la bitácora.
 *
 * @param id            identificador de la fila.
 * @param tipo          tipo del evento.
 * @param nivel         INFO, WARN o ERROR.
 * @param descripcion   detalle.
 * @param fechaHora     fecha y hora.
 * @param usuarioCodigo usuario asociado (sin FK, puede no existir).
 * @param ip            IP de origen.
 * @param mensajeId     mensaje asociado, o {@code null}.
 * @param idConexion    conexión asociada, o {@code null}.
 */
public record LogDTO(long id, String tipo, String nivel, String descripcion,
                     LocalDateTime fechaHora, String usuarioCodigo, String ip,
                     String mensajeId, String idConexion) {
}
