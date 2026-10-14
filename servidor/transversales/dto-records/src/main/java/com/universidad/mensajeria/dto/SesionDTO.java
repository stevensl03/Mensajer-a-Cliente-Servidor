package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Una sesión activa. Un usuario puede tener varias.
 *
 * @param idSesion       identificador de la sesión (igual al id del canal).
 * @param codigo         código del usuario.
 * @param nombreCompleto nombres y apellidos.
 * @param ip             IP remota del cliente.
 * @param desde          momento del login.
 */
public record SesionDTO(String idSesion, String codigo, String nombreCompleto,
                        String ip, LocalDateTime desde) {

    public SesionDTO {
        Objects.requireNonNull(idSesion, "idSesion");
        Objects.requireNonNull(desde, "desde");
    }
}
