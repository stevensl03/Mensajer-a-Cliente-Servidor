package com.universidad.mensajeria.persistencia.dao;

import com.universidad.mensajeria.dto.ConexionDTO;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code conexiones_usuario} (multi-sesión).
 */
public interface ConexionDao {

    /**
     * Inserta una conexión nueva.
     *
     * @param conexion conexión a guardar.
     */
    void guardar(ConexionDTO conexion);

    /**
     * Actualiza una conexión (por ejemplo, su fecha de desconexión y motivo).
     *
     * @param conexion conexión a actualizar.
     */
    void actualizar(ConexionDTO conexion);

    /**
     * @param idConexion identificador de la conexión.
     * @return la conexión si existe.
     */
    Optional<ConexionDTO> buscar(String idConexion);

    /** @return las conexiones sin fecha de desconexión. */
    List<ConexionDTO> listarActivas();

    /**
     * @param codigoUsuario código del usuario.
     * @return todas las conexiones del usuario.
     */
    List<ConexionDTO> listarPorUsuario(String codigoUsuario);
}
