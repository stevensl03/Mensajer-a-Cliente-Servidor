package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.SesionDTO;

import java.time.LocalDateTime;

/**
 * Tabla {@code conexiones_usuario}.
 */
public interface IRepositorioConexiones {

    /** @param s sesión abierta. */
    void abrir(SesionDTO s);

    /**
     * @param idSesion sesión cerrada.
     * @param fin      momento del cierre.
     */
    void cerrar(String idSesion, LocalDateTime fin);

    /**
     * Cierra las conexiones que quedaron abiertas tras un apagado abrupto.
     *
     * @param fin momento del cierre.
     * @return cantidad de conexiones cerradas.
     */
    int cerrarHuerfanas(LocalDateTime fin);
}
