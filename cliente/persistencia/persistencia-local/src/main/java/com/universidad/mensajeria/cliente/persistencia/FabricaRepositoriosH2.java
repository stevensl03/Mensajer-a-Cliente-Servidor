package com.universidad.mensajeria.cliente.persistencia;

import java.util.Objects;

/** Repositorios H2 sobre un mismo proveedor de conexiones. */
public final class FabricaRepositoriosH2 {

    private final IProveedorConexionBD proveedor;
    private final IRepositorioSesion sesiones;
    private final IRepositorioMensajesLocal mensajes;

    public FabricaRepositoriosH2(IProveedorConexionBD proveedor) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.sesiones = new RepositorioSesionH2(proveedor);
        this.mensajes = new RepositorioMensajesH2(proveedor);
    }

    /** @return el repositorio de sesiones (siempre la misma instancia). */
    public IRepositorioSesion sesiones() {
        return sesiones;
    }

    /** @return el repositorio de mensajes (siempre la misma instancia). */
    public IRepositorioMensajesLocal mensajes() {
        return mensajes;
    }
}
