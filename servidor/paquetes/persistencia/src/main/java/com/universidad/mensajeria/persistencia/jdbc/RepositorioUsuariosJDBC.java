package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;

import java.util.List;

/**
 * Tabla {@code usuarios} con JDBC.
 */
final class RepositorioUsuariosJDBC implements IRepositorioUsuarios {

    @Override
    public List<UsuarioRegistroDTO> listarTodos() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public int contar() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public void guardarTodos(List<UsuarioRegistroDTO> l) {
        throw new UnsupportedOperationException("Fase 2");
    }
}
