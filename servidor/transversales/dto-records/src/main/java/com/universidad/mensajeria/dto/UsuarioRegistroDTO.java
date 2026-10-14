package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Usuario para persistencia. Incluye el hash de la contraseña, que la tabla
 * {@code usuarios} exige como {@code contrasena_hash CHAR(64) NOT NULL}.
 *
 * @param codigo         código o documento del usuario.
 * @param nombres        nombres.
 * @param apellidos      apellidos.
 * @param contrasenaHash hash SHA-256 de la contraseña (64 caracteres).
 * @param programa       programa académico.
 * @param rol            USUARIO o ADMIN.
 */
public record UsuarioRegistroDTO(String codigo, String nombres, String apellidos,
                                 String contrasenaHash, String programa, Rol rol) {

    public UsuarioRegistroDTO {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(contrasenaHash, "contrasenaHash");
        Objects.requireNonNull(programa, "programa");
        Objects.requireNonNull(rol, "rol");
    }
}
