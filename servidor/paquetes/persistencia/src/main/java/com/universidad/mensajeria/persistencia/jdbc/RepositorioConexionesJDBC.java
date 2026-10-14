package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioConexiones;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Tabla {@code conexiones_usuario} con JDBC (solo historial).
 */
final class RepositorioConexionesJDBC implements IRepositorioConexiones {

    private static final String ABRIR =
            "INSERT INTO conexiones_usuario (id_conexion, codigo_usuario, ip_remota, fecha_conexion) VALUES (?, ?, ?, ?)";
    private static final String CERRAR =
            "UPDATE conexiones_usuario SET fecha_desconexion = ? WHERE id_conexion = ?";
    private static final String CERRAR_HUERFANAS =
            "UPDATE conexiones_usuario SET fecha_desconexion = ? WHERE fecha_desconexion IS NULL";

    private final IProveedorConexionBD proveedor;

    RepositorioConexionesJDBC(IProveedorConexionBD proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public void abrir(SesionDTO s) {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(ABRIR)) {
            ps.setString(1, s.idSesion());
            ps.setString(2, s.codigo());
            ps.setString(3, s.ip());
            ps.setObject(4, s.desde());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar la conexión " + s.idSesion(), e);
        }
    }

    @Override
    public void cerrar(String idSesion, LocalDateTime fin) {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(CERRAR)) {
            ps.setObject(1, fin);
            ps.setString(2, idSesion);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo cerrar la conexión " + idSesion, e);
        }
    }

    @Override
    public int cerrarHuerfanas(LocalDateTime fin) {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(CERRAR_HUERFANAS)) {
            ps.setObject(1, fin);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron cerrar las conexiones huérfanas", e);
        }
    }
}
