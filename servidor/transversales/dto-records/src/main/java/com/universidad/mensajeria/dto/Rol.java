package com.universidad.mensajeria.dto;

/**
 * Rol de un usuario registrado.
 *
 * <p>Vive en DTO-Records (no en el componente Usuarios) porque los records
 * transversales lo referencian.</p>
 */
public enum Rol {
    USUARIO,
    ADMIN
}
