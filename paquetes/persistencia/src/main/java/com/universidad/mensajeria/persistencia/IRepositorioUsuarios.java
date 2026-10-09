package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.UsuarioRegistroDTO;

import java.util.List;
import java.util.Optional;

/**
 * Almacenamiento de los usuarios registrados (con el hash de su contraseña).
 */
public interface IRepositorioUsuarios {

    /**
     * @param c código del usuario.
     * @return el usuario, si existe.
     */
    Optional<UsuarioRegistroDTO> buscarPorCodigo(String c);

    /** @return todos los usuarios. */
    List<UsuarioRegistroDTO> listarTodos();

    /**
     * Inserta o actualiza los usuarios indicados.
     *
     * @param l usuarios a guardar.
     */
    void guardarTodos(List<UsuarioRegistroDTO> l);
}
