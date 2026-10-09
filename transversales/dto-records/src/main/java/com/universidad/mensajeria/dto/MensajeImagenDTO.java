package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos de un mensaje de imagen con sus 6 versiones. Incluye {@code idConexion}
 * para la trazabilidad multi-sesión (C-04).
 *
 * @param id                 UUID del mensaje.
 * @param remitenteCodigo    emisor.
 * @param destinatarioCodigo destinatario o {@code null} si es difusión.
 * @param idConexion         conexión desde la que se envió, o {@code null}.
 * @param canal              FORO o PRIVADO.
 * @param hashSha256         hash SHA-256 de la imagen original.
 * @param fechaEnvio         fecha y hora de envío.
 * @param versiones          las 6 versiones en orden 0 a 5.
 */
public record MensajeImagenDTO(String id, String remitenteCodigo, String destinatarioCodigo,
                               String idConexion, String canal, String hashSha256,
                               LocalDateTime fechaEnvio, List<ImagenVersionDTO> versiones) {
}
