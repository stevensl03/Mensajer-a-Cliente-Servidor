package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.dto.SolicitudMensajeDTO;

/**
 * Callback que ejecutan los hilos consumidores. Lo implementa la fachada
 * ({@code ProcesadorMensajes}); forma parte del contrato InterfazGestorMensajes.
 */
@FunctionalInterface
public interface IConsumidorMensajes {

    /** @param s solicitud a procesar. */
    void procesar(SolicitudMensajeDTO s);
}
