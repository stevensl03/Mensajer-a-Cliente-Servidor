package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

/**
 * LOGOUT: cierra la sesión, responde y cierra el canal.
 */
public final class ManejadorLogout implements IManejadorComando {

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        throw new UnsupportedOperationException("Fase 6");
    }
}
