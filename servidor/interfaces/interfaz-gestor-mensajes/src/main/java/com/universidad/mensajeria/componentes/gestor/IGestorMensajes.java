package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.dto.SolicitudMensajeDTO;

/**
 * Cola productor/consumidor de mensajes entrantes (InterfazGestorMensajes).
 * El hilo de red solo encola; N hilos consumidores procesan.
 */
public interface IGestorMensajes {

    /**
     * Arranca los hilos consumidores.
     *
     * @param hilos número de consumidores.
     * @param c     quien procesa cada solicitud.
     */
    void iniciar(int hilos, IConsumidorMensajes c);

    /**
     * @param s solicitud recibida.
     * @return {@code false} si la cola está llena (nunca bloquea).
     */
    boolean encolar(SolicitudMensajeDTO s);

    /** @return solicitudes pendientes en la cola. */
    int pendientes();

    /** Deja de aceptar solicitudes y vacía la cola dentro de un plazo. */
    void detener();
}
