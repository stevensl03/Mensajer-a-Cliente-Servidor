package com.universidad.mensajeria.cliente.comunicacion.tcp;

import com.universidad.mensajeria.cliente.comunicacion.IReceptorServidor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Receptor que guarda lo recibido y cuenta los avisos de desconexión. */
class ReceptorGrabador implements IReceptorServidor {

    final BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
    final BlockingQueue<String> desconexiones = new LinkedBlockingQueue<>();
    final AtomicInteger avisos = new AtomicInteger();

    @Override
    public void alRecibir(String json) {
        recibidos.add(json);
    }

    @Override
    public void alDesconectar(String motivo) {
        avisos.incrementAndGet();
        desconexiones.add(motivo);
    }

    String siguiente() throws InterruptedException {
        return recibidos.poll(5, TimeUnit.SECONDS);
    }

    String desconexion() throws InterruptedException {
        return desconexiones.poll(5, TimeUnit.SECONDS);
    }
}
