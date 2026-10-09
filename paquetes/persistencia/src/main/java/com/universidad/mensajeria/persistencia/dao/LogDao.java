package com.universidad.mensajeria.persistencia.dao;

import com.universidad.mensajeria.dto.LogDTO;

import java.util.List;

/**
 * Acceso a la tabla {@code logs_eventos}.
 */
public interface LogDao {

    /**
     * Inserta un log.
     *
     * @param log evento a registrar.
     */
    void guardar(LogDTO log);

    /** @return todos los logs. */
    List<LogDTO> listar();

    /**
     * @param codigoUsuario código del usuario.
     * @return los logs asociados al usuario.
     */
    List<LogDTO> listarPorUsuario(String codigoUsuario);
}
