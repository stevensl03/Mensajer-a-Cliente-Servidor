package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;

import java.util.Collection;

/**
 * Lógica del protocolo cliente-servidor (interfaz "InterfazClienteServidor"
 * del diagrama): interpreta cada petición, independientemente del transporte.
 */
public interface IClienteServidor {

    /**
     * Atiende una petición. Nunca lanza excepciones por errores de negocio:
     * los devuelve como respuesta con {@code exito = false}.
     *
     * @param p      petición recibida.
     * @param origen canal por el que llegó (identifica al remitente).
     * @return la respuesta para el cliente.
     */
    RespuestaDTO procesar(PeticionDTO p, ICanalCliente origen);

    /**
     * Envía un evento a varios canales (solo encola, no bloquea).
     *
     * @param e        evento.
     * @param destinos canales destino.
     */
    void notificar(EventoPushDTO e, Collection<ICanalCliente> destinos);
}
