package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Fila del informe USUARIOS_CONECTADOS.
 *
 * @param codigo           código del usuario.
 * @param nombreCompleto   nombres y apellidos.
 * @param cantidadSesiones cantidad de veces que está conectado ahora.
 */
public record ConexionUsuarioDTO(String codigo, String nombreCompleto, int cantidadSesiones) {

    public ConexionUsuarioDTO {
        Objects.requireNonNull(codigo, "codigo");
        if (cantidadSesiones < 0) {
            throw new IllegalArgumentException("cantidadSesiones no puede ser negativo: " + cantidadSesiones);
        }
    }
}
