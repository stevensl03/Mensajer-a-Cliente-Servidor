package com.universidad.mensajeria.componentes.protocolo;

import java.io.IOException;

/**
 * Transporte de red del servidor (InterfazProtocoloComunicacion). Las
 * implementaciones (hoy TCP) son intercambiables.
 */
public interface IProtocoloComunicacion {

    /**
     * Abre el puerto y empieza a aceptar clientes. No bloquea.
     *
     * @param puerto puerto de escucha ({@code 0} elige uno libre).
     * @param r      receptor de los eventos de cada conexión.
     * @throws IOException si no se puede abrir el puerto.
     */
    void iniciar(int puerto, IReceptorConexiones r) throws IOException;

    /** Deja de aceptar clientes y cierra las conexiones. Idempotente. */
    void detener();

    /** @return {@code true} si el servidor está aceptando clientes. */
    boolean estaActivo();

    /** @return la IP de la máquina del servidor, para mostrarla a los usuarios. */
    String getIpServidor();
}
