package com.universidad.mensajeria.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Respuesta del servidor a una {@link PeticionDTO}.
 *
 * <p>El cliente la distingue de un {@link EventoPushDTO} porque trae el campo
 * {@code idPeticion} (que puede ser {@code null} si la petición no se pudo
 * leer).</p>
 *
 * @param idPeticion identificador de la petición respondida.
 * @param exito      {@code true} si la petición se atendió correctamente.
 * @param mensaje    descripción legible del resultado.
 * @param datos      datos del resultado, o {@code null}.
 */
public record RespuestaDTO(String idPeticion, boolean exito, String mensaje, JsonNode datos) {

    /** Respuesta exitosa. */
    public static RespuestaDTO ok(String idPeticion, String mensaje, JsonNode datos) {
        return new RespuestaDTO(idPeticion, true, mensaje, datos);
    }

    /** Respuesta de error, sin datos. */
    public static RespuestaDTO error(String idPeticion, String mensaje) {
        return new RespuestaDTO(idPeticion, false, mensaje, null);
    }
}
