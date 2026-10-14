package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.ISerializador;

/**
 * Crea la implementación de {@link IClienteServidor} (Factory).
 */
public final class FabricaClienteServidor {

    private FabricaClienteServidor() {
    }

    /**
     * @param json serializador JSON.
     * @param cfg  configuración (límites de contenido).
     * @return el protocolo cliente-servidor.
     */
    public static IClienteServidor crear(ISerializador json, ConfiguracionServidor cfg) {
        throw new UnsupportedOperationException("Fase 4d");
    }
}
