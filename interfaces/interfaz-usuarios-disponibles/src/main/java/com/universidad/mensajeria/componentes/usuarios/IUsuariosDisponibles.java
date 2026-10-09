package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Optional;

/**
 * Usuarios registrados y autenticación (interfaz
 * "InterfazUsuariosDisponibles" del diagrama).
 */
public interface IUsuariosDisponibles {

    /**
     * Valida código y contraseña.
     *
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
     * @return {@code true} si el usuario existe y tiene rol ADMIN.
     */
    boolean esAdmin(String codigo);
}
