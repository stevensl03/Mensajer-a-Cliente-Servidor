package com.universidad.mensajeria.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Respuesta del servidor a una {@link PeticionDTO}. Siempre trae
 * {@code idPeticion}; así el cliente la distingue de un {@link EventoPushDTO}.
 *
 * @param idPeticion identificador de la petición respondida.
 * @param exito      {@code true} si la petición se atendió correctamente.
 * @param codigo     código del resultado ({@link CodigoError#OK} si hubo éxito).
 * @param mensaje    descripción legible del resultado.
 * @param datos      datos del resultado, o {@code null}.
 */
public record RespuestaDTO(String idPeticion, boolean exito, CodigoError codigo,
                           String mensaje, JsonNode datos) {

    public RespuestaDTO {
        Objects.requireNonNull(codigo, "codigo");
        if (exito != (codigo == CodigoError.OK)) {
            throw new IllegalArgumentException("exito=" + exito + " no corresponde al código " + codigo);
        }
    }
}
