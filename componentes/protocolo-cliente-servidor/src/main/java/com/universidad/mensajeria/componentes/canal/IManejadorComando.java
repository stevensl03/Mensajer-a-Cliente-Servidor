package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;

/**
 * Atiende un comando del protocolo (patrón Command).
 */
@FunctionalInterface
public interface IManejadorComando {

    /**
     * @param p      petición recibida.
     * @param origen canal del remitente.
     * @return la respuesta para el cliente.
     */
    RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente origen);
}
