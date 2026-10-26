package com.universidad.mensajeria.cliente.comunicacion;

import com.universidad.mensajeria.cliente.comunicacion.tcp.ConexionTCP;

import java.util.Objects;

/** Crea el transporte según {@code servidor.protocolo}. Punto de extensión para otro protocolo. */
public final class FabricaConexiones {

    /**
     * @param t             protocolo configurado.
     * @param maxTramaBytes tamaño máximo de una trama.
     * @return una conexión sin abrir.
     */
    public IConexionServidor crear(TipoProtocolo t, int maxTramaBytes) {
        return switch (Objects.requireNonNull(t, "t")) {
            case TCP -> new ConexionTCP(maxTramaBytes);
        };
    }
}
