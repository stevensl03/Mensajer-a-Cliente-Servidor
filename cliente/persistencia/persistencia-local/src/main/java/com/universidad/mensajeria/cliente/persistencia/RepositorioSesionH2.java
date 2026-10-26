package com.universidad.mensajeria.cliente.persistencia;

import com.universidad.mensajeria.dto.UsuarioDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Objects;

/** {@link IRepositorioSesion} sobre la tabla {@code SESION}. */
public final class RepositorioSesionH2 implements IRepositorioSesion {

    private final IProveedorConexionBD proveedor;

    public RepositorioSesionH2(IProveedorConexionBD proveedor) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
    }

    @Override
    public long abrir(UsuarioDTO u, String ipServidor, int puerto) {
        Objects.requireNonNull(u, "u");
        Objects.requireNonNull(ipServidor, "ipServidor");
        String sql = "INSERT INTO SESION (CODIGO_USUARIO, IP_SERVIDOR, PUERTO, INICIO) VALUES (?, ?, ?, ?)";
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.codigo());
            ps.setString(2, ipServidor);
            ps.setInt(3, puerto);
            ps.setObject(4, LocalDateTime.now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new IllegalStateException("H2 no devolvió el id de la sesión");
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar la sesión local: " + e.getMessage(), e);
        }
    }

    @Override
    public void cerrar(long id) {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement("UPDATE SESION SET FIN = ? WHERE ID = ? AND FIN IS NULL")) {
            ps.setObject(1, LocalDateTime.now());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo cerrar la sesión local " + id + ": " + e.getMessage(), e);
        }
    }
}
