package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Mensaje procesado, con todo su detalle.
 *
 * @param id            UUID del mensaje.
 * @param tipo          TEXTO o IMAGEN.
 * @param canal         FORO o PRIVADO.
 * @param remitente     código del emisor.
 * @param destinatario  código del destinatario, o {@code null} si es FORO.
 * @param fecha         fecha y hora de envío.
 * @param hash          hash SHA-256 del contenido original.
 * @param texto         texto (solo TEXTO).
 * @param numCaracteres caracteres (0 si es imagen).
 * @param numPalabras   palabras (0 si es imagen).
 * @param nombreArchivo nombre del archivo (solo IMAGEN).
 * @param imagenes      las 6 versiones de la imagen (vacía si es texto).
 */
public record MensajeDTO(String id, TipoMensaje tipo, Canal canal, String remitente,
                         String destinatario, LocalDateTime fecha, String hash,
                         String texto, int numCaracteres, int numPalabras,
                         String nombreArchivo, List<ImagenVersionDTO> imagenes) {

    public MensajeDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(remitente, "remitente");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(hash, "hash");
        if ((canal == Canal.FORO) != (destinatario == null)) {
            throw new IllegalArgumentException("FORO no lleva destinatario y PRIVADO lo requiere");
        }
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
    }
}
