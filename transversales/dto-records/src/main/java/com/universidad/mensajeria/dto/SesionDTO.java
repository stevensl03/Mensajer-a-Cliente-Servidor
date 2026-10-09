package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una sesión (conexión) activa. Un usuario puede tener varias.
 *
 * @param idConexion     identificador de la conexión.
 * @param codigo         código del usuario, o {@code null} si aún no inició sesión.
 * @param nombreCompleto nombres y apellidos, o {@code null} si aún no inició sesión.
 * @param ip             IP remota del cliente.
 * @param desde          momento de la conexión.
 */
public record SesionDTO(String idConexion, String codigo, String nombreCompleto,
                        String ip, LocalDateTime desde) {

    public SesionDTO {
        Objects.requireNonNull(idConexion, "idConexion");
        Objects.requireNonNull(desde, "desde");
    }
}
