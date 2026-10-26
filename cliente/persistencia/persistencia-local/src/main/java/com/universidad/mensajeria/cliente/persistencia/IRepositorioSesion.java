package com.universidad.mensajeria.cliente.persistencia;

import com.universidad.mensajeria.dto.UsuarioDTO;

/** Sesiones locales: una fila por login correcto. */
public interface IRepositorioSesion {

    /**
     * @param u          usuario autenticado.
     * @param ipServidor IP del servidor ({@code INFO_SERVIDOR}).
     * @param puerto     puerto del servidor.
     * @return el id de la sesión local.
     */
    long abrir(UsuarioDTO u, String ipServidor, int puerto);

    /**
     * Registra el fin de la sesión. Idempotente: una sesión ya cerrada conserva su hora de fin.
     *
     * @param id sesión local.
     */
    void cerrar(long id);
}
