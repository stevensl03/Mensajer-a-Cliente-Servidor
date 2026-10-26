package com.universidad.mensajeria.cliente.servicios.informes;

import com.universidad.mensajeria.dto.InformeDTO;

import java.util.Objects;

/**
 * Un informe y el JSON tal como llegó, con sangría (D-08). Las {@code filas}
 * del informe son {@code Map<String,Object>} (R-19).
 *
 * @param informe informe leído.
 * @param json    {@code datos} de la respuesta, con sangría.
 */
public record InformeRecibido(InformeDTO informe, String json) {

    public InformeRecibido {
        Objects.requireNonNull(informe, "informe");
        Objects.requireNonNull(json, "json");
    }
}
