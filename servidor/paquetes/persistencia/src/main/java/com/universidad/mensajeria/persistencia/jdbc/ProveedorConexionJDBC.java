package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.persistencia.IDialectoSQL;
import com.universidad.mensajeria.persistencia.IProveedorConexionBD;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Pool de conexiones (HikariCP) configurado con {@code bd.properties}, y
 * migraciones Flyway en la carpeta del motor.
 */
public final class ProveedorConexionJDBC implements IProveedorConexionBD {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorConexionJDBC.class);

    private final HikariDataSource pool;
    private final IDialectoSQL dialecto;

    /**
     * @param bd       claves {@code bd.url}, {@code bd.usuario}, {@code bd.clave} y {@code bd.pool}.
     * @param dialecto dialecto del motor configurado en {@code bd.motor}.
     */
    public ProveedorConexionJDBC(Properties bd, IDialectoSQL dialecto) {
        Objects.requireNonNull(bd, "bd");
        this.dialecto = Objects.requireNonNull(dialecto, "dialecto");
        var config = new HikariConfig();
        config.setPoolName("mensajeria-bd");
        config.setJdbcUrl(requerida(bd, "bd.url"));
        config.setUsername(requerida(bd, "bd.usuario"));
        config.setPassword(bd.getProperty("bd.clave", ""));
        config.setMaximumPoolSize(Integer.parseInt(requerida(bd, "bd.pool")));
        this.pool = new HikariDataSource(config);
    }

    /** Aplica las migraciones pendientes de {@code db/migration/{motor}}. */
    public void migrar() {
        var resultado = Flyway.configure()
                .dataSource(pool)
                .locations("classpath:" + dialecto.carpetaMigraciones())
                .load()
                .migrate();
        LOG.info("Migraciones aplicadas: {} (esquema en la versión {})",
                resultado.migrationsExecuted, resultado.targetSchemaVersion);
    }

    @Override
    public Connection obtenerConexion() throws SQLException {
        return pool.getConnection();
    }

    /** Cierra el pool (apagado ordenado). Idempotente. */
    public void cerrar() {
        pool.close();
    }

    private static String requerida(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la clave de configuración '" + clave + "'");
        }
        return valor.strip();
    }
}
