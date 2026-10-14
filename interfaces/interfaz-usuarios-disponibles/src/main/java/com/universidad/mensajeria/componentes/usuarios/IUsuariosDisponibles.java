package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de usuarios registrados y autenticación
 * (InterfazUsuariosDisponibles). Sin CRUD.
 */
public interface IUsuariosDisponibles {

    /**
     * @param codigo código del usuario.
     * @param clave  contraseña en claro recibida del cliente.
     * @return el usuario si las credenciales son válidas.
     */
    Optional<UsuarioDTO> autenticar(String codigo, String clave);

    /** @return todos los usuarios registrados. */
    List<UsuarioDTO> listarRegistrados();

    /**
     * @param codigo código del usuario.
     * @return el usuario registrado, si existe.
     */
    Optional<UsuarioDTO> buscar(String codigo);

    /**
     * @param codigo código del usuario.
     * @return {@code true} si el usuario está registrado.
     */
    boolean existe(String codigo);

    /** @return el único usuario con rol ADMIN. */
    UsuarioDTO administrador();
}
