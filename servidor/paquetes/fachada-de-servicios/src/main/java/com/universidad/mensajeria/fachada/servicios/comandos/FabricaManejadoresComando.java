package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.TipoComando;

import java.util.EnumMap;

/**
 * Registra un manejador por cada {@link TipoComando} (Factory).
 */
public final class FabricaManejadoresComando {

    /** @return un manejador por cada comando del protocolo. */
    public EnumMap<TipoComando, IManejadorComando> crearTodos() {
        throw new UnsupportedOperationException("Fase 6");
    }
}
