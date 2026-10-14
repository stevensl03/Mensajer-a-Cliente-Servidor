package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;

/**
 * Crea las entidades {@link Mensaje} (Factory).
 */
public final class FabricaMensajes {

    /**
     * @param s solicitud de texto.
     * @param r resultado del procesamiento.
     * @return el mensaje de texto.
     */
    public Mensaje deTexto(SolicitudMensajeDTO s, ResultadoTextoDTO r) {
        throw new UnsupportedOperationException("Fase 5");
    }

    /**
     * @param s solicitud de imagen.
     * @param r resultado del procesamiento.
     * @return el mensaje de imagen.
     */
    public Mensaje deImagen(SolicitudMensajeDTO s, ResultadoImagenDTO r) {
        throw new UnsupportedOperationException("Fase 5");
    }
}
