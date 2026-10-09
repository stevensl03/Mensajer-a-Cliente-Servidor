package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Vista de un mensaje para las capas superiores y los informes.
 *
 * @param id             UUID del mensaje.
 * @param tipo           TEXTO o IMAGEN.
 * @param canal          FORO o PRIVADO.
 * @param remitente      código del emisor.
 * @param destinatario   código del destinatario, o {@code null} si es FORO.
 * @param fecha          fecha y hora de envío.
 * @param hash           hash SHA-256 del contenido original.
 * @param numCaracteres  caracteres (0 si es imagen).
 * @param numPalabras    palabras (0 si es imagen).
 * @param rutasArchivos  rutas en el directorio de almacenamiento.
 */
public record MensajeDTO(String id, TipoMensaje tipo, Canal canal, String remitente,
                         String destinatario, LocalDateTime fecha, String hash,
                         int numCaracteres, int numPalabras, List<String> rutasArchivos) {

    public MensajeDTO {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(canal, "canal");
        rutasArchivos = rutasArchivos == null ? List.of() : List.copyOf(rutasArchivos);
    }
}
