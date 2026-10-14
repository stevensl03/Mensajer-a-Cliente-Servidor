package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioLogs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabla {@code logs_eventos} con JDBC.
 */
final class RepositorioLogsJDBC implements IRepositorioLogs {

    private static final String GUARDAR =
            "INSERT INTO logs_eventos (id, tipo, descripcion, fecha_hora, usuario_codigo, ip) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String LISTAR =
            "SELECT id, tipo, descripcion, fecha_hora, usuario_codigo, ip FROM logs_eventos ORDER BY fecha_hora, id";

    private final IProveedorConexionBD proveedor;

    RepositorioLogsJDBC(IProveedorConexionBD proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public void guardar(EventoLogDTO e) {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(GUARDAR)) {
            ps.setString(1, e.id());
            ps.setString(2, e.tipo().name());
            ps.setString(3, e.descripcion());
            ps.setObject(4, e.fecha());
            ps.setString(5, e.usuario());
            ps.setString(6, e.ip());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo guardar el evento " + e.id(), ex);
        }
    }

    @Override
    public List<EventoLogDTO> listarTodos() {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(LISTAR);
             ResultSet rs = ps.executeQuery()) {
            var eventos = new ArrayList<EventoLogDTO>();
            while (rs.next()) {
                eventos.add(new EventoLogDTO(rs.getString("id"), TipoEvento.valueOf(rs.getString("tipo")),
                        rs.getString("descripcion"), rs.getString("usuario_codigo"), rs.getString("ip"),
                        rs.getObject("fecha_hora", LocalDateTime.class)));
            }
            return eventos;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron listar los eventos", e);
        }
    }
}
