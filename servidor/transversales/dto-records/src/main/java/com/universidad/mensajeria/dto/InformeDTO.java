package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Informe generado.
 *
 * @param tipo        tipo de informe.
 * @param generado    fecha y hora de generación.
 * @param generadoPor código de quien lo pidió, o {@code null}.
 * @param filas       filas del informe (el tipo depende de {@code tipo}).
 */
public record InformeDTO(TipoInforme tipo, LocalDateTime generado, String generadoPor, List<?> filas) {

    public InformeDTO {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(generado, "generado");
        filas = filas == null ? List.of() : List.copyOf(filas);
    }
}
