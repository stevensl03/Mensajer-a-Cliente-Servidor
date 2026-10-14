package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;

/**
 * Contrato de la conversación cliente-servidor (InterfazClienteServidor):
 * JSON a DTO y viceversa, y validación de peticiones. No conoce sockets.
 */
public interface IClienteServidor {

    /**
     * @param json trama recibida.
     * @return la petición validada.
     * @throws PeticionInvalidaException si el JSON es inválido o la petición está incompleta.
     */
    PeticionDTO interpretar(String json);

    /**
     * @param r respuesta.
     * @return su JSON.
     */
    String serializar(RespuestaDTO r);

    /**
     * @param e evento push.
     * @return su JSON.
     */
    String serializar(EventoPushDTO e);

    /**
     * @param idPeticion petición respondida.
     * @param datos      datos del resultado, o {@code null}.
     * @return una respuesta exitosa.
     */
    RespuestaDTO exito(String idPeticion, Object datos);

    /**
     * @param idPeticion petición respondida (puede ser {@code null}).
     * @param c          código de error.
     * @param detalle    descripción legible.
     * @return una respuesta de error.
     */
    RespuestaDTO error(String idPeticion, CodigoError c, String detalle);
}
