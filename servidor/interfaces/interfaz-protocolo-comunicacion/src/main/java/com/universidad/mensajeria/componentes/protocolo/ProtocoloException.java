package com.universidad.mensajeria.componentes.protocolo;

import java.io.IOException;

/**
 * Error de codificación o decodificación del protocolo (trama o mensaje
 * inválido). Extiende {@link IOException} porque se produce leyendo la red.
 */
public class ProtocoloException extends IOException {

    /**
     * @param mensaje descripción del error.
     */
    public ProtocoloException(String mensaje) {
        super(mensaje);
    }

    /**
     * @param mensaje descripción del error.
     * @param causa   causa original.
     */
    public ProtocoloException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
