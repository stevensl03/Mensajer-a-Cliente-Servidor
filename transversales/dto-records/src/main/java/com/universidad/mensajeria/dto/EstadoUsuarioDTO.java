package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Usuario registrado con su estado de conexión (comando LISTAR_USUARIOS).
 *
 * @param usuario   usuario registrado.
 * @param conectado {@code true} si tiene al menos una sesión activa.
 * @param sesiones  número de sesiones activas.
 */
public record EstadoUsuarioDTO(UsuarioDTO usuario, boolean conectado, int sesiones) {

    public EstadoUsuarioDTO {
        Objects.requireNonNull(usuario, "usuario");
        if (sesiones < 0) {
            throw new IllegalArgumentException("sesiones no puede ser negativo: " + sesiones);
        }
    }
}
