package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Contenido de un mensaje enviado por un cliente.
 *
 * @param tipo          TEXTO o IMAGEN.
 * @param texto         texto del mensaje (TEXTO).
 * @param nombreArchivo nombre del archivo (IMAGEN).
 * @param datosBase64   bytes de la imagen en Base64 (IMAGEN).
 */
public record ContenidoDTO(TipoMensaje tipo, String texto, String nombreArchivo, String datosBase64) {

    public ContenidoDTO {
        Objects.requireNonNull(tipo, "tipo");
    }
}
