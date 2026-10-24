package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

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
        exigirTipo(s, TipoMensaje.TEXTO);
        return new MensajeTexto(s.idMensaje(), s.canal(), s.remitente(), s.destinatario(), s.recibido(),
                r.hash(), s.contenido().texto(), r.numCaracteres(), r.numPalabras());
    }

    /**
     * @param s solicitud de imagen.
     * @param r resultado del procesamiento.
     * @return el mensaje de imagen.
     */
    public Mensaje deImagen(SolicitudMensajeDTO s, ResultadoImagenDTO r) {
        exigirTipo(s, TipoMensaje.IMAGEN);
        return new MensajeImagen(s.idMensaje(), s.canal(), s.remitente(), s.destinatario(), s.recibido(),
                r.hash(), s.contenido().nombreArchivo(), r.imagenes());
    }

    private static void exigirTipo(SolicitudMensajeDTO s, TipoMensaje esperado) {
        if (s.contenido().tipo() != esperado) {
            throw new IllegalArgumentException("Se esperaba contenido " + esperado + " y llegó " + s.contenido().tipo());
        }
    }
}
