package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.persistencia.dao.ConexionDao;
import com.universidad.mensajeria.persistencia.dao.InformeDao;
import com.universidad.mensajeria.persistencia.dao.LogDao;
import com.universidad.mensajeria.persistencia.dao.MensajeDao;
import com.universidad.mensajeria.persistencia.dao.UsuarioDao;

import java.sql.SQLException;

/**
 * Acceso a datos (paquete "Persistencia" del diagrama). Expone los repositorios
 * por tabla y gestiona la conexión a MySQL, que se configura por archivo.
 *
 * <p>{@code AlmacenarInformación} depende de esta interfaz, no de la
 * implementación, respetando la inversión de dependencias.</p>
 */
public interface IPersistencia {

    /**
     * Abre la conexión a MySQL.
     *
     * @throws SQLException si falla la conexión.
     */
    void conectar() throws SQLException;

    /** Cierra la conexión. */
    void desconectar();

    /** @return el repositorio de usuarios. */
    UsuarioDao usuarios();

    /** @return el repositorio de conexiones. */
    ConexionDao conexiones();

    /** @return el repositorio de mensajes (texto e imagen). */
    MensajeDao mensajes();

    /** @return el repositorio de logs. */
    LogDao logs();

    /** @return el repositorio de informes. */
    InformeDao informes();
}
