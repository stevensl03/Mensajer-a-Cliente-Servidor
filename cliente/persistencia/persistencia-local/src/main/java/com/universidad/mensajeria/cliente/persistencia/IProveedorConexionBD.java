package com.universidad.mensajeria.cliente.persistencia;

import java.sql.Connection;

/** Base de datos local, una por usuario. Se abre después del login. */
public interface IProveedorConexionBD {

    /**
     * Crea o abre la base del usuario y asegura el esquema. Si había otra
     * abierta, la cierra antes.
     *
     * @param codigoUsuario código del usuario ({@code [A-Za-z0-9_-]{1,20}}).
     * @throws IllegalArgumentException si el código no es válido (R-52).
     * @throws IllegalStateException    si no se puede abrir la base.
     */
    void abrir(String codigoUsuario);

    /**
     * @return una conexión nueva que quien la pide debe cerrar.
     * @throws IllegalStateException si la base no está abierta.
     */
    Connection obtenerConexion();

    /** Cierra la base abierta. Idempotente. */
    void cerrar();
}
