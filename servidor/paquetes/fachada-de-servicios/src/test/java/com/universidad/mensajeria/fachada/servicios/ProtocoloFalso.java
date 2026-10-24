package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;

/** Transporte sin red: guarda el receptor para que la prueba simule conexiones y tramas. */
final class ProtocoloFalso implements IProtocoloComunicacion {

    volatile IReceptorConexiones receptor;
    volatile int puerto;
    volatile boolean activo;
    volatile int detenciones;

    @Override
    public void iniciar(int puerto, IReceptorConexiones r) {
        this.puerto = puerto;
        this.receptor = r;
        this.activo = true;
    }

    @Override
    public void detener() {
        activo = false;
        detenciones++;
    }

    @Override
    public boolean estaActivo() {
        return activo;
    }

    @Override
    public String getIpServidor() {
        return "192.168.1.10";
    }
}
