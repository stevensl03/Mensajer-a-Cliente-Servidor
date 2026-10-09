package com.universidad.mensajeria.persistencia.dao;

import com.universidad.mensajeria.dto.InformeDTO;

import java.util.List;

/**
 * Acceso a la tabla {@code informes}.
 */
public interface InformeDao {

    /**
     * Guarda un informe.
     *
     * @param informe informe a persistir.
     */
    void guardar(InformeDTO informe);

    /** @return todos los informes generados. */
    List<InformeDTO> listar();
}
