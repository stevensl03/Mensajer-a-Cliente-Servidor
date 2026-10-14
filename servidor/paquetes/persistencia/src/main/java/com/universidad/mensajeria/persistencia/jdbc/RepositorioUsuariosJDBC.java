package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla {@code usuarios} con JDBC.
 */
final class RepositorioUsuariosJDBC implements IRepositorioUsuarios {

    private static final String LISTAR =
            "SELECT codigo, nombres, apellidos, contrasena_hash, programa, rol FROM usuarios ORDER BY codigo";
    private static final String CONTAR = "SELECT COUNT(*) FROM usuarios";
    private static final String INSERTAR =
            "INSERT INTO usuarios (codigo, nombres, apellidos, contrasena_hash, programa, rol) VALUES (?, ?, ?, ?, ?, ?)";

    private final IProveedorConexionBD proveedor;

    RepositorioUsuariosJDBC(IProveedorConexionBD proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public List<UsuarioRegistroDTO> listarTodos() {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(LISTAR);
             ResultSet rs = ps.executeQuery()) {
            var usuarios = new ArrayList<UsuarioRegistroDTO>();
            while (rs.next()) {
                usuarios.add(new UsuarioRegistroDTO(rs.getString("codigo"), rs.getString("nombres"),
                        rs.getString("apellidos"), rs.getString("contrasena_hash"), rs.getString("programa"),
                        Rol.valueOf(rs.getString("rol"))));
            }
            return usuarios;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron listar los usuarios", e);
        }
    }

    @Override
    public int contar() {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(CONTAR);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron contar los usuarios", e);
        }
    }

    @Override
    public void guardarTodos(List<UsuarioRegistroDTO> l) {
        try (Connection c = proveedor.obtenerConexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(INSERTAR)) {
                for (UsuarioRegistroDTO u : l) {
                    ps.setString(1, u.codigo());
                    ps.setString(2, u.nombres());
                    ps.setString(3, u.apellidos());
                    ps.setString(4, u.contrasenaHash());
                    ps.setString(5, u.programa());
                    ps.setString(6, u.rol().name());
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron guardar los usuarios", e);
        }
    }
}
