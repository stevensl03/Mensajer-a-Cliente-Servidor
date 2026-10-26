package com.universidad.mensajeria.cliente.persistencia;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** {@link IRepositorioMensajesLocal} sobre las tablas {@code MENSAJE} y {@code ADJUNTO}. */
public final class RepositorioMensajesH2 implements IRepositorioMensajesLocal {

    /** SQLState de clave duplicada. */
    private static final String DUPLICADA = "23505";
    /** SQLState de clave foránea inexistente. */
    private static final String SIN_PADRE = "23506";

    private static final String INSERTAR = """
            INSERT INTO MENSAJE (ID, SESION_ID, CANAL, REMITENTE, DESTINATARIO, CONVERSACION, TIPO, TEXTO,
                                 NOMBRE_ARCHIVO, HASH, NUM_CARACTERES, NUM_PALABRAS, FECHA, PROPIO)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String CONVERSACION = """
            SELECT ID, CANAL, REMITENTE, DESTINATARIO, TIPO, TEXTO, NOMBRE_ARCHIVO, HASH,
                   NUM_CARACTERES, NUM_PALABRAS, FECHA
            FROM MENSAJE WHERE CONVERSACION = ? ORDER BY FECHA, ID""";
    private static final String GUARDAR_ADJUNTO = """
            MERGE INTO ADJUNTO (MENSAJE_ID, ETAPA, NOMBRE_ARCHIVO, DATOS) KEY (MENSAJE_ID, ETAPA)
            VALUES (?, ?, ?, ?)""";

    private final IProveedorConexionBD proveedor;

    public RepositorioMensajesH2(IProveedorConexionBD proveedor) {
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
    }

    /**
     * {@code INSERT} que ignora la clave duplicada: a diferencia de
     * {@code MERGE … KEY(ID)}, no reescribe {@code SESION_ID} ni {@code PROPIO}
     * (R-54) y es atómico aunque push e historial lleguen a la vez.
     */
    @Override
    public void guardar(MensajeDTO m, long sesion, boolean propio) {
        Objects.requireNonNull(m, "m");
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(INSERTAR)) {
            ps.setString(1, m.id());
            ps.setLong(2, sesion);
            ps.setString(3, m.canal().name());
            ps.setString(4, m.remitente());
            ps.setString(5, m.destinatario());
            ps.setString(6, IRepositorioMensajesLocal.claveConversacion(m));
            ps.setString(7, m.tipo().name());
            if (m.texto() == null) {
                ps.setNull(8, Types.CLOB);
            } else {
                ps.setString(8, m.texto());
            }
            ps.setString(9, m.nombreArchivo());
            ps.setString(10, m.hash());
            ps.setInt(11, m.numCaracteres());
            ps.setInt(12, m.numPalabras());
            ps.setObject(13, m.fecha());
            ps.setBoolean(14, propio);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (!DUPLICADA.equals(e.getSQLState())) {
                throw new IllegalStateException("No se pudo guardar el mensaje " + m.id() + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void guardarAdjunto(String idMensaje, TipoFiltro etapa, String nombre, byte[] datos) {
        Objects.requireNonNull(idMensaje, "idMensaje");
        Objects.requireNonNull(etapa, "etapa");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(datos, "datos");
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(GUARDAR_ADJUNTO)) {
            ps.setString(1, idMensaje);
            ps.setString(2, etapa.name());
            ps.setString(3, nombre);
            ps.setBytes(4, datos);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (SIN_PADRE.equals(e.getSQLState())) {
                throw new IllegalArgumentException("El mensaje " + idMensaje + " no está guardado", e);
            }
            throw new IllegalStateException("No se pudo guardar la imagen " + etapa + " de " + idMensaje
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<byte[]> adjunto(String idMensaje, TipoFiltro etapa) {
        Objects.requireNonNull(idMensaje, "idMensaje");
        Objects.requireNonNull(etapa, "etapa");
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT DATOS FROM ADJUNTO WHERE MENSAJE_ID = ? AND ETAPA = ?")) {
            ps.setString(1, idMensaje);
            ps.setString(2, etapa.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBytes(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la imagen " + etapa + " de " + idMensaje
                    + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<MensajeDTO> conversacion(String clave) {
        Objects.requireNonNull(clave, "clave");
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(CONVERSACION)) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                var lista = new ArrayList<MensajeDTO>();
                while (rs.next()) {
                    lista.add(leer(rs));
                }
                return List.copyOf(lista);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la conversación " + clave + ": " + e.getMessage(), e);
        }
    }

    @Override
    public long contar() {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM MENSAJE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron contar los mensajes: " + e.getMessage(), e);
        }
    }

    private static MensajeDTO leer(ResultSet rs) throws SQLException {
        return new MensajeDTO(
                rs.getString("ID"),
                TipoMensaje.valueOf(rs.getString("TIPO")),
                Canal.valueOf(rs.getString("CANAL")),
                rs.getString("REMITENTE"),
                rs.getString("DESTINATARIO"),
                rs.getObject("FECHA", LocalDateTime.class),
                rs.getString("HASH").strip(),
                rs.getString("TEXTO"),
                rs.getInt("NUM_CARACTERES"),
                rs.getInt("NUM_PALABRAS"),
                rs.getString("NOMBRE_ARCHIVO"),
                List.of());
    }
}
