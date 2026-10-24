package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.gestor.IConsumidorMensajes;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Cola sin hilos: graba lo encolado; con {@code llena} rechaza como una cola sin espacio. */
final class GestorMensajesFalso implements IGestorMensajes {

    final List<SolicitudMensajeDTO> encolados = new CopyOnWriteArrayList<>();
    volatile boolean llena;
    volatile IConsumidorMensajes consumidor;
    volatile int hilos;
    volatile boolean detenido;

    @Override
    public void iniciar(int hilos, IConsumidorMensajes c) {
        this.hilos = hilos;
        this.consumidor = c;
    }

    @Override
    public boolean encolar(SolicitudMensajeDTO s) {
        if (llena) {
            return false;
        }
        encolados.add(s);
        return true;
    }

    @Override
    public int pendientes() {
        return encolados.size();
    }

    @Override
    public void detener() {
        detenido = true;
    }
}
