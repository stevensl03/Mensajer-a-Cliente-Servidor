package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Mensaje de texto con sus conteos.
 */
public final class MensajeTexto extends Mensaje {

    private final String texto;
    private final int numCaracteres;
    private final int numPalabras;

    public MensajeTexto(String id, Canal canal, String remitente, String destinatario,
                        LocalDateTime fecha, String hash,
                        String texto, int numCaracteres, int numPalabras) {
        super(id, canal, remitente, destinatario, fecha, hash);
        this.texto = Objects.requireNonNull(texto, "texto");
        if (numCaracteres < 0 || numPalabras < 0) {
            throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        }
        this.numCaracteres = numCaracteres;
        this.numPalabras = numPalabras;
    }

    @Override
    public TipoMensaje tipo() {
        return TipoMensaje.TEXTO;
    }

    @Override
    public MensajeDTO aDTO() {
        return new MensajeDTO(id, tipo(), canal, remitente, destinatario, fecha, hash,
                texto, numCaracteres, numPalabras, null, List.of());
    }
}
