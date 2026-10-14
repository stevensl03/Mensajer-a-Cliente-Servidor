package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

/**
 * LOGIN {codigo, clave}: autentica y abre la sesión respetando el cupo global.
 */
public final class ManejadorLogin implements IManejadorComando {

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        throw new UnsupportedOperationException("Fase 6");
    }
}
