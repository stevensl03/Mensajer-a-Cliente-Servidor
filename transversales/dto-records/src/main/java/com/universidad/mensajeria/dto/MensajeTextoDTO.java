package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;

/**
 * Datos de un mensaje de texto. Incluye {@code idConexion} para la trazabilidad
 * multi-sesión (C-04).
 *
 * @param id                 UUID del mensaje.
 * @param remitenteCodigo    emisor.
 * @param destinatarioCodigo destinatario o {@code null} si es difusión.
 * @param idConexion         conexión desde la que se envió, o {@code null}.
 * @param canal              FORO o PRIVADO.
 * @param contenido          texto.
 * @param numCaracteres      número de caracteres.
 * @param numPalabras        número de palabras.
 * @param rutaArchivo        copia en el directorio de almacenamiento.
 * @param hashSha256         hash SHA-256 del contenido.
 * @param fechaEnvio         fecha y hora de envío.
 */
public record MensajeTextoDTO(String id, String remitenteCodigo, String destinatarioCodigo,
                              String idConexion, String canal, String contenido,
                              int numCaracteres, int numPalabras, String rutaArchivo,
                              String hashSha256, LocalDateTime fechaEnvio) {
}
