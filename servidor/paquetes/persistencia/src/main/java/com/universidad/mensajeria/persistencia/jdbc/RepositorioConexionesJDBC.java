package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.persistencia.IRepositorioConexiones;

import java.time.LocalDateTime;

/**
 * Tabla {@code conexiones_usuario} con JDBC.
 */
final class RepositorioConexionesJDBC implements IRepositorioConexiones {

    @Override
    public void abrir(SesionDTO s) {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public void cerrar(String idSesion, LocalDateTime fin) {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public int cerrarHuerfanas(LocalDateTime fin) {
        throw new UnsupportedOperationException("Fase 2");
    }
}
