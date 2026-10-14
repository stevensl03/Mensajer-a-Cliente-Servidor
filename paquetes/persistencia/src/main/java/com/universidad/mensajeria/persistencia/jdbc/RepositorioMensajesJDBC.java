package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.persistencia.IRepositorioMensajes;

import java.util.List;

/**
 * Tablas de mensajes con JDBC; un mensaje y sus imágenes en una transacción.
 */
final class RepositorioMensajesJDBC implements IRepositorioMensajes {

    @Override
    public void guardar(MensajeDTO m) {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public List<MensajeDTO> listarTodos() {
        throw new UnsupportedOperationException("Fase 2");
    }
}
