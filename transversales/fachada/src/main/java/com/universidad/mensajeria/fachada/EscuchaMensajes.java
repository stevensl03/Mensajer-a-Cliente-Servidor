package com.universidad.mensajeria.fachada;

import com.universidad.mensajeria.dto.MensajeDTO;

/**
 * Observer para que la capa de presentación muestre los mensajes que llegan
 * (RF-12).
 *
 * <p>El contrato vive en la fachada, de modo que la dirección de dependencia
 * sigue siendo presentación → fachada.</p>
 */
@FunctionalInterface
public interface EscuchaMensajes {

    /**
     * Se invoca cada vez que el servidor procesa un mensaje.
     *
     * @param mensaje vista del mensaje procesado.
     */
    void alRecibirMensaje(MensajeDTO mensaje);
}
