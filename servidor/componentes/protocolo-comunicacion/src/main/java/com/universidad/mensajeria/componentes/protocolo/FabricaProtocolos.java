package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.componentes.protocolo.tcp.ServidorTCP;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;

import java.util.Objects;

/**
 * Crea el transporte configurado (Factory). Punto de extensión: un protocolo
 * nuevo es un {@code case} aquí más su subpaquete; nada más cambia.
 */
public final class FabricaProtocolos {

    private FabricaProtocolos() {
    }

    /**
     * @param t   protocolo.
     * @param cfg configuración del servidor.
     * @return el transporte, sin iniciar.
     */
    public static IProtocoloComunicacion crear(TipoProtocolo t, ConfiguracionServidor cfg) {
        Objects.requireNonNull(cfg, "cfg");
        return switch (Objects.requireNonNull(t, "t")) {
            case TCP -> new ServidorTCP(cfg.maxTramaBytes(), cfg.colaSalida());
        };
    }
}
