package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

/**
 * Un caso de uso por comando del protocolo (patrón Command).
 */
@FunctionalInterface
public interface IManejadorComando {

    /**
     * @param p petición recibida.
     * @param c canal del remitente.
     * @return la respuesta para el cliente.
     */
    RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c);
}
