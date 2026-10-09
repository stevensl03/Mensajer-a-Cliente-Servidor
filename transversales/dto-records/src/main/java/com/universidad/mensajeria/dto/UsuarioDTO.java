package com.universidad.mensajeria.dto;

/**
 * Usuario registrado expuesto a la lógica y la presentación. No expone el hash
 * de la contraseña.
 *
 * @param codigo    código o documento del usuario.
 * @param nombres   nombres.
 * @param apellidos apellidos.
 * @param programa  programa académico.
 * @param rol       USUARIO o ADMIN.
 */
public record UsuarioDTO(String codigo, String nombres, String apellidos,
                         String programa, Rol rol) {
}
