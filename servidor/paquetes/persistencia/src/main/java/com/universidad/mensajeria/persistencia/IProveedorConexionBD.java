package com.universidad.mensajeria.persistencia;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Entrega conexiones JDBC del pool.
 */
public interface IProveedorConexionBD {

    /**
     * @return una conexión que el llamador debe cerrar.
     * @throws SQLException si no se puede obtener.
     */
    Connection obtenerConexion() throws SQLException;
}
