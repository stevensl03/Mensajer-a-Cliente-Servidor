package com.universidad.mensajeria.componentes.protocolo;

import java.io.IOException;

/**
 * Transporte de red del servidor (interfaz "InterfazProtocoloComunicacion"
 * del diagrama). Las implementaciones (TCP, UDP, gRPC) son intercambiables.
 */
public interface IProtocoloComunicacion {

    /**
     * Abre el puerto y empieza a aceptar clientes. No bloquea.
     *
     * @param puerto puerto de escucha ({@code 0} elige uno libre).
     * @throws IOException si no se puede abrir el puerto.
     */
    void iniciar(int puerto) throws IOException;

    /** Deja de aceptar clientes y cierra las conexiones. Idempotente. */
    void detener();

    /** @return {@code true} si el servidor está aceptando clientes. */
    boolean estaActivo();

    /** @return la IP de la máquina del servidor, para mostrarla a los usuarios. */
    String getIpServidor();
}
