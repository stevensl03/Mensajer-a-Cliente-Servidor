package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.dto.SolicitudMensajeDTO;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Clase principal del componente GestorColasDeMensajes: cola productor /
 * consumidor de mensajes entrantes. El hilo de red solo encola; N hilos
 * consumidores procesan. No conoce Mensajes ni la red.
 */
public final class GestorColasDeMensajes implements IGestorMensajes {

    private final BlockingQueue<SolicitudMensajeDTO> cola;

    /**
     * @param capacidad capacidad de la cola ({@code cola.capacidad}).
     */
    public GestorColasDeMensajes(int capacidad) {
        this.cola = new LinkedBlockingQueue<>(capacidad);
    }

    @Override
    public void iniciar(int hilos, IConsumidorMensajes c) {
        throw new UnsupportedOperationException("Fase 4c");
    }

    @Override
    public boolean encolar(SolicitudMensajeDTO s) {
        throw new UnsupportedOperationException("Fase 4c");
    }

    @Override
    public int pendientes() {
        return cola.size();
    }

    @Override
    public void detener() {
        throw new UnsupportedOperationException("Fase 4c");
    }
}
