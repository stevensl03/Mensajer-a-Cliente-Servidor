package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.persistencia.IRepositorioLogs;

import java.util.List;

/**
 * Tabla {@code logs_eventos} con JDBC.
 */
final class RepositorioLogsJDBC implements IRepositorioLogs {

    @Override
    public void guardar(EventoLogDTO e) {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public List<EventoLogDTO> listarTodos() {
        throw new UnsupportedOperationException("Fase 2");
    }
}
