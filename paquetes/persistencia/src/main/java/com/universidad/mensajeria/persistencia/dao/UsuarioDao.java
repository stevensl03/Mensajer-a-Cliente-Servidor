package com.universidad.mensajeria.persistencia.dao;

import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code usuarios}.
 *
 * <p>Para el upsert inicial se usa {@link UsuarioRegistroDTO}, que incluye el
 * hash de la contraseña (C-05).</p>
 */
public interface UsuarioDao {

    /**
     * Inserta o actualiza (upsert) un usuario con su hash.
     *
     * @param usuario usuario con hash a guardar.
     */
    void guardar(UsuarioRegistroDTO usuario);

    /**
     * Upsert de varios usuarios con sus hashes.
     *
     * @param usuarios usuarios con hash a guardar.
     */
    void guardarTodos(List<UsuarioRegistroDTO> usuarios);

    /**
     * @param codigo código del usuario.
     * @return el usuario con su hash, si existe.
     */
    Optional<UsuarioRegistroDTO> buscar(String codigo);

    /** @return todos los usuarios (sin exponer el hash). */
    List<UsuarioDTO> listar();
}
