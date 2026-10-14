package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.persistencia.AlmacenamientoDirectorioLocal;
import com.universidad.mensajeria.persistencia.FabricaRepositorios;
import com.universidad.mensajeria.persistencia.IAlmacenamientoArchivos;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioConexiones;
import com.universidad.mensajeria.persistencia.IRepositorioLogs;
import com.universidad.mensajeria.persistencia.IRepositorioMensajes;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;

import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link FabricaRepositorios} sobre JDBC portable (MySQL / PostgreSQL). Crea
 * cada repositorio una sola vez.
 */
public final class FabricaRepositoriosJDBC implements FabricaRepositorios {

    private final IProveedorConexionBD proveedor;
    private final IDialectoSQL dialecto;
    private final IRepositorioUsuarios usuarios;
    private final IRepositorioConexiones conexiones;
    private final IRepositorioMensajes mensajes;
    private final IRepositorioLogs logs;
    private final IAlmacenamientoArchivos archivos;

    /**
     * @param proveedor    pool de conexiones.
     * @param dialecto     dialecto del motor ({@code bd.motor}).
     * @param raizArchivos raíz del directorio de archivos ({@code archivos.directorio}).
     */
    public FabricaRepositoriosJDBC(IProveedorConexionBD proveedor, IDialectoSQL dialecto, Path raizArchivos) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.dialecto = Objects.requireNonNull(dialecto, "dialecto");
        this.usuarios = new RepositorioUsuariosJDBC(proveedor);
        this.conexiones = new RepositorioConexionesJDBC(proveedor);
        this.mensajes = new RepositorioMensajesJDBC(proveedor);
        this.logs = new RepositorioLogsJDBC(proveedor);
        this.archivos = new AlmacenamientoDirectorioLocal(raizArchivos);
    }

    @Override
    public IRepositorioUsuarios usuarios() {
        return usuarios;
    }

    @Override
    public IRepositorioConexiones conexiones() {
        return conexiones;
    }

    @Override
    public IRepositorioMensajes mensajes() {
        return mensajes;
    }

    @Override
    public IRepositorioLogs logs() {
        return logs;
    }

    @Override
    public IAlmacenamientoArchivos archivos() {
        return archivos;
    }
}
