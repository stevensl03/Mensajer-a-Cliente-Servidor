package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;

/**
 * Instantánea de un informe generado.
 *
 * @param tipo          USUARIOS_REGISTRADOS, USUARIOS_CONECTADOS, MENSAJES o LOGS.
 * @param generadoEn    fecha y hora de generación.
 * @param generadoPor   usuario que lo generó, o {@code null}.
 * @param contenidoJson contenido serializado del informe.
 */
public record InformeDTO(String tipo, LocalDateTime generadoEn,
                         String generadoPor, String contenidoJson) {
}
