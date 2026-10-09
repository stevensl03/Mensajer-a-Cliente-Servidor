package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.persistencia.dao.ConexionDao;
import com.universidad.mensajeria.persistencia.dao.InformeDao;
import com.universidad.mensajeria.persistencia.dao.LogDao;
import com.universidad.mensajeria.persistencia.dao.MensajeDao;
import com.universidad.mensajeria.persistencia.dao.UsuarioDao;

import java.sql.SQLException;

/**
 * Implementación del acceso a datos sobre MySQL (paquete "Persistencia").
 *
 * <p>Recibe los datos de conexión (leídos de {@code bd.properties} por la raíz
 * de composición) y crea los DAOs.</p>
 */
public class Persistencia implements IPersistencia {

    private final String url;
    private final String usuario;
    private final String clave;

    /**
     * @param url     URL JDBC.
     * @param usuario usuario de la base de datos.
     * @param clave   contraseña de la base de datos.
     */
    public Persistencia(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    @Override
    public void conectar() throws SQLException {
        // TODO: DriverManager.getConnection(url, usuario, clave).
    }

    @Override
    public void desconectar() {
        // TODO
    }

    @Override
    public UsuarioDao usuarios() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public ConexionDao conexiones() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public MensajeDao mensajes() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public LogDao logs() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public InformeDao informes() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
