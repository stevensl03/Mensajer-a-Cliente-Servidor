package com.universidad.mensajeria.cliente.persistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * H2 embebida, una base por usuario: {@code {codigo}} de la URL se reemplaza
 * por el código del usuario ({@code jdbc:h2:./data/cliente_{codigo}}).
 *
 * <p>Mientras la base está abierta se mantiene una conexión de guarda, para
 * que H2 no la cierre entre operaciones; cada operación pide su propia
 * conexión con {@link #obtenerConexion()}.</p>
 */
public final class ProveedorConexionH2 implements IProveedorConexionBD {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorConexionH2.class);
    /** Mismo marcador que {@code ConfiguracionCliente.MARCADOR_CODIGO}. */
    static final String MARCADOR = "{codigo}";
    /** El código se interpola en una ruta y en una URL JDBC (R-52). */
    private static final Pattern CODIGO_VALIDO = Pattern.compile("[A-Za-z0-9_-]{1,20}");

    private final String url;
    private final String usuario;
    private final String clave;
    private final ReentrantLock cambio = new ReentrantLock();
    private volatile Connection guarda;
    private volatile String urlAbierta;
    private volatile String codigoAbierto;

    /**
     * @param url     URL JDBC con el marcador {@value #MARCADOR}.
     * @param usuario usuario de H2.
     * @param clave   clave de H2 (puede ser vacía).
     */
    public ProveedorConexionH2(String url, String usuario, String clave) {
        this.url = Objects.requireNonNull(url, "url");
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.clave = Objects.requireNonNull(clave, "clave");
        if (!url.contains(MARCADOR)) {
            throw new IllegalArgumentException("La URL de H2 debe contener " + MARCADOR + ": " + url);
        }
    }

    @Override
    public void abrir(String codigoUsuario) {
        Objects.requireNonNull(codigoUsuario, "codigoUsuario");
        if (!CODIGO_VALIDO.matcher(codigoUsuario).matches()) {
            throw new IllegalArgumentException("Código de usuario inválido para la base local: " + codigoUsuario);
        }
        cambio.lock();
        try {
            if (guarda != null && codigoUsuario.equals(codigoAbierto)) {
                return;
            }
            cerrar();
            String destino = url.replace(MARCADOR, codigoUsuario);
            try {
                Connection c = DriverManager.getConnection(destino, usuario, clave);
                try {
                    inicializarEsquema(c);
                } catch (SQLException e) {
                    c.close();
                    throw e;
                }
                guarda = c;
                urlAbierta = destino;
                codigoAbierto = codigoUsuario;
                LOG.info("Base local abierta para {}", codigoUsuario);
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo abrir la base local de " + codigoUsuario
                        + ": " + e.getMessage(), e);
            }
        } finally {
            cambio.unlock();
        }
    }

    @Override
    public Connection obtenerConexion() {
        String destino = urlAbierta;
        if (guarda == null || destino == null) {
            throw new IllegalStateException("La base local no está abierta");
        }
        try {
            return DriverManager.getConnection(destino, usuario, clave);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo obtener una conexión local: " + e.getMessage(), e);
        }
    }

    @Override
    public void cerrar() {
        cambio.lock();
        try {
            Connection c = guarda;
            guarda = null;
            urlAbierta = null;
            codigoAbierto = null;
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException e) {
                    LOG.warn("Fallo cerrando la base local: {}", e.getMessage());
                }
            }
        } finally {
            cambio.unlock();
        }
    }

    /** Ejecuta {@code schema.sql} (idempotente). */
    private static void inicializarEsquema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("RUNSCRIPT FROM 'classpath:/schema.sql'");
        }
    }
}
