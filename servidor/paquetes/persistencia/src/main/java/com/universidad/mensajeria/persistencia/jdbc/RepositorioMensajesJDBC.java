package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.persistencia.IRepositorioMensajes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tablas {@code mensajes}, {@code mensajes_texto} e {@code imagenes_mensaje}.
 * Un mensaje y su detalle se guardan en una sola transacción.
 */
final class RepositorioMensajesJDBC implements IRepositorioMensajes {

    private static final String INSERTAR_MENSAJE = "INSERT INTO mensajes (id, remitente_codigo, destinatario_codigo, "
            + "conversacion_id, canal, tipo_mensaje, fecha_envio, hash_sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERTAR_TEXTO =
            "INSERT INTO mensajes_texto (mensaje_id, contenido, num_caracteres, num_palabras) VALUES (?, ?, ?, ?)";
    private static final String INSERTAR_IMAGEN = "INSERT INTO imagenes_mensaje (id, mensaje_id, etapa, "
            + "nombre_archivo, ruta_archivo, tamano_bytes, hash_sha256) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String LISTAR_MENSAJES = "SELECT m.id, m.remitente_codigo, m.destinatario_codigo, m.canal, "
            + "m.tipo_mensaje, m.fecha_envio, m.hash_sha256, t.contenido, t.num_caracteres, t.num_palabras "
            + "FROM mensajes m LEFT JOIN mensajes_texto t ON t.mensaje_id = m.id ORDER BY m.fecha_envio, m.id";
    private static final String LISTAR_IMAGENES = "SELECT mensaje_id, etapa, nombre_archivo, ruta_archivo, "
            + "tamano_bytes, hash_sha256 FROM imagenes_mensaje ORDER BY mensaje_id";

    private final IProveedorConexionBD proveedor;

    RepositorioMensajesJDBC(IProveedorConexionBD proveedor) {
        this.proveedor = proveedor;
    }

    @Override
    public void guardar(MensajeDTO m) {
        try (Connection c = proveedor.obtenerConexion()) {
            c.setAutoCommit(false);
            try {
                insertarCabecera(c, m);
                if (m.tipo() == TipoMensaje.TEXTO) {
                    insertarTexto(c, m);
                } else {
                    insertarImagenes(c, m);
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo guardar el mensaje " + m.id(), e);
        }
    }

    @Override
    public List<MensajeDTO> listarTodos() {
        try (Connection c = proveedor.obtenerConexion()) {
            Map<String, List<ImagenVersionDTO>> imagenes = leerImagenes(c);
            var mensajes = new ArrayList<MensajeDTO>();
            try (PreparedStatement ps = c.prepareStatement(LISTAR_MENSAJES);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mensajes.add(aDTO(rs, imagenes.getOrDefault(rs.getString("id"), List.of())));
                }
            }
            return mensajes;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron listar los mensajes", e);
        }
    }

    /** Misma regla que {@code Mensaje.claveConversacion}: {@code menor|mayor}; {@code null} en FORO. */
    static String conversacion(MensajeDTO m) {
        if (m.canal() == Canal.FORO) {
            return null;
        }
        String a = m.remitente();
        String b = m.destinatario();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static void insertarCabecera(Connection c, MensajeDTO m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERTAR_MENSAJE)) {
            ps.setString(1, m.id());
            ps.setString(2, m.remitente());
            ps.setString(3, m.destinatario());
            ps.setString(4, conversacion(m));
            ps.setString(5, m.canal().name());
            ps.setString(6, m.tipo().name());
            ps.setObject(7, m.fecha());
            ps.setString(8, m.hash());
            ps.executeUpdate();
        }
    }

    private static void insertarTexto(Connection c, MensajeDTO m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERTAR_TEXTO)) {
            ps.setString(1, m.id());
            ps.setString(2, m.texto());
            ps.setInt(3, m.numCaracteres());
            ps.setInt(4, m.numPalabras());
            ps.executeUpdate();
        }
    }

    private static void insertarImagenes(Connection c, MensajeDTO m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERTAR_IMAGEN)) {
            for (ImagenVersionDTO v : m.imagenes()) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, m.id());
                ps.setString(3, v.etapa().name());
                ps.setString(4, v.nombreArchivo());
                ps.setString(5, v.rutaArchivo());
                ps.setLong(6, v.tamanoBytes());
                ps.setString(7, v.hash());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Map<String, List<ImagenVersionDTO>> leerImagenes(Connection c) throws SQLException {
        Map<String, List<ImagenVersionDTO>> porMensaje = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(LISTAR_IMAGENES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TipoFiltro etapa = TipoFiltro.valueOf(rs.getString("etapa"));
                porMensaje.computeIfAbsent(rs.getString("mensaje_id"), k -> new ArrayList<>())
                        .add(new ImagenVersionDTO(etapa.ordinal(), etapa, rs.getString("nombre_archivo"),
                                rs.getString("ruta_archivo"), rs.getLong("tamano_bytes"), rs.getString("hash_sha256")));
            }
        }
        porMensaje.values().forEach(l -> l.sort(Comparator.comparingInt(ImagenVersionDTO::orden)));
        return porMensaje;
    }

    private static MensajeDTO aDTO(ResultSet rs, List<ImagenVersionDTO> imagenes) throws SQLException {
        TipoMensaje tipo = TipoMensaje.valueOf(rs.getString("tipo_mensaje"));
        String nombreArchivo = imagenes.stream()
                .filter(v -> v.etapa() == TipoFiltro.ORIGINAL)
                .map(ImagenVersionDTO::nombreArchivo)
                .findFirst().orElse(null);
        return new MensajeDTO(rs.getString("id"), tipo, Canal.valueOf(rs.getString("canal")),
                rs.getString("remitente_codigo"), rs.getString("destinatario_codigo"),
                rs.getObject("fecha_envio", LocalDateTime.class), rs.getString("hash_sha256"),
                rs.getString("contenido"), rs.getInt("num_caracteres"), rs.getInt("num_palabras"),
                nombreArchivo, imagenes);
    }
}
