package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.fachada.ICanalCliente;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Canal sin red que graba las tramas enviadas. */
final class CanalGrabador implements ICanalCliente {

    private final String id = UUID.randomUUID().toString();
    private final List<String> enviados = new CopyOnWriteArrayList<>();
    private volatile boolean abierto = true;
    boolean fallarAlEnviar;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getIpRemota() {
        return "10.0.0.5";
    }

    @Override
    public void enviar(String json) {
        if (fallarAlEnviar) {
            throw new IllegalStateException("canal caído");
        }
        enviados.add(json);
    }

    @Override
    public void cerrar() {
        abierto = false;
    }

    @Override
    public boolean estaAbierto() {
        return abierto;
    }

    List<String> enviados() {
        return List.copyOf(enviados);
    }
}
