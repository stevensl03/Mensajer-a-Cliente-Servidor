package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Mensaje recibido y aún sin procesar; es lo que viaja por la cola de
 * mensajes entrantes.
 *
 * @param idMensaje    UUID asignado al recibirlo.
 * @param idPeticion   petición que lo originó, o {@code null} si lo envía el ADMIN.
 * @param idSesion     sesión del remitente, o {@code null} si lo envía el ADMIN.
 * @param remitente    código del remitente (siempre tomado de la sesión).
 * @param canal        FORO o PRIVADO.
 * @param destinatario código del destinatario, o {@code null} si es FORO.
 * @param contenido    contenido recibido.
 * @param recibido     momento de recepción.
 */
public record SolicitudMensajeDTO(String idMensaje, String idPeticion, String idSesion,
                                  String remitente, Canal canal, String destinatario,
                                  ContenidoDTO contenido, LocalDateTime recibido) {

    public SolicitudMensajeDTO {
        Objects.requireNonNull(idMensaje, "idMensaje");
        Objects.requireNonNull(remitente, "remitente");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(recibido, "recibido");
    }
}
