package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.EventoLogDTO;

import java.util.List;

/**
 * Tabla {@code logs_eventos}.
 */
public interface IRepositorioLogs {

    /** @param e evento a guardar. */
    void guardar(EventoLogDTO e);

    /** @return todos los eventos guardados. */
    List<EventoLogDTO> listarTodos();
}
