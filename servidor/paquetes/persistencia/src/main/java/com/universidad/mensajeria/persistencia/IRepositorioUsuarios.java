package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.UsuarioRegistroDTO;

import java.util.List;

/**
 * Tabla {@code usuarios}.
 */
public interface IRepositorioUsuarios {

    /** @return todos los usuarios registrados. */
    List<UsuarioRegistroDTO> listarTodos();

    /** @return cantidad de usuarios registrados. */
    int contar();

    /** @param l usuarios a guardar (carga inicial). */
    void guardarTodos(List<UsuarioRegistroDTO> l);
}
