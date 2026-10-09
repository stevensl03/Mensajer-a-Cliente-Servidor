package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.Collection;
import java.util.List;

/**
 * Registro de las conexiones activas y del cupo global de sesiones.
 */
public interface IGestorConexiones {

    /**
     * Registra una conexión nueva si hay cupo.
     *
     * @param c canal recién aceptado.
     * @return {@code false} si se alcanzó el máximo de sesiones.
     */
    boolean registrar(ICanalCliente c);

    /**
     * Asocia un usuario autenticado a una conexión registrada (también lo
     * asocia al canal).
     *
     * @param id identificador de la conexión.
     * @param u  usuario autenticado.
     */
    void asociarUsuario(String id, UsuarioDTO u);

    /**
     * Cierra una conexión y libera su cupo. Idempotente.
     *
     * @param id identificador de la conexión.
     */
    void cerrar(String id);

    /**
     * @param codigo código de usuario.
     * @return las conexiones activas de ese usuario (puede tener varias).
     */
    List<ICanalCliente> sesionesDe(String codigo);

    /** @return todas las conexiones activas. */
    Collection<ICanalCliente> todas();

    /** @return el resumen de las conexiones activas, de la más antigua a la más reciente. */
    List<SesionDTO> resumen();

    /** @return {@code true} si se puede registrar al menos una conexión más. */
    boolean hayCupo();
}
