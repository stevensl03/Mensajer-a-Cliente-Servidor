package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensaje de texto con sus métricas (caracteres y palabras).
 */
public final class MensajeTexto extends Mensaje {

    private final String contenido;
    private final int numCaracteres;
    private final int numPalabras;

    public MensajeTexto(UUID id, Canal canal, String remitente, String destinatario,
                        LocalDateTime fechaEnvio, String hashSha256,
                        String contenido, int numCaracteres, int numPalabras) {
        super(id, canal, remitente, destinatario, fechaEnvio, hashSha256);
        this.contenido = Objects.requireNonNull(contenido, "contenido");
        if (numCaracteres < 0 || numPalabras < 0) {
            throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        }
        this.numCaracteres = numCaracteres;
        this.numPalabras = numPalabras;
    }

    @Override
    public TipoMensaje getTipo() {
        return TipoMensaje.TEXTO;
    }

    public String getContenido() {
        return contenido;
    }

    public int getNumCaracteres() {
        return numCaracteres;
    }

    public int getNumPalabras() {
        return numPalabras;
    }
}
