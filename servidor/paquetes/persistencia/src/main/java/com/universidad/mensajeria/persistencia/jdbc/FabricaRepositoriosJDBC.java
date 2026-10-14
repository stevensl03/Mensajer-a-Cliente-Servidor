package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.persistencia.FabricaRepositorios;
import com.universidad.mensajeria.persistencia.IAlmacenamientoArchivos;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioConexiones;
import com.universidad.mensajeria.persistencia.IRepositorioLogs;
import com.universidad.mensajeria.persistencia.IRepositorioMensajes;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;

import java.util.Objects;

/**
 * {@link FabricaRepositorios} sobre JDBC portable (MySQL / PostgreSQL).
 */
public final class FabricaRepositoriosJDBC implements FabricaRepositorios {

    private final IProveedorConexionBD proveedor;
    private final IDialectoSQL dialecto;

    public FabricaRepositoriosJDBC(IProveedorConexionBD proveedor, IDialectoSQL dialecto) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.dialecto = Objects.requireNonNull(dialecto, "dialecto");
    }

    @Override
    public IRepositorioUsuarios usuarios() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public IRepositorioConexiones conexiones() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public IRepositorioMensajes mensajes() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public IRepositorioLogs logs() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public IAlmacenamientoArchivos archivos() {
        throw new UnsupportedOperationException("Fase 2");
    }
}
