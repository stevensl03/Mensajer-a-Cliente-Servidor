package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidad común a todo mensaje (paquete "Mensajes" del diagrama).
 *
 * <p>La jerarquía es cerrada: solo existen mensajes de texto y de imagen.
 * Inmutable y por tanto segura entre hilos.</p>
 */
public abstract sealed class Mensaje permits MensajeTexto, MensajeImagen {

    protected final UUID id;
    protected final Canal canal;
    protected final String remitente;
    protected final String destinatario;
    protected final LocalDateTime fechaEnvio;
    protected final String hashSha256;

    /**
     * @param id           identificador del mensaje.
     * @param canal        FORO o PRIVADO.
     * @param remitente    código del emisor (lo asigna el servidor desde la sesión).
     * @param destinatario código del destinatario; {@code null} si y solo si es FORO.
     * @param fechaEnvio   fecha y hora de envío.
     * @param hashSha256   hash SHA-256 del contenido original.
     */
    protected Mensaje(UUID id, Canal canal, String remitente, String destinatario,
                      LocalDateTime fechaEnvio, String hashSha256) {
        this.id = Objects.requireNonNull(id, "id");
        this.canal = Objects.requireNonNull(canal, "canal");
        this.remitente = Objects.requireNonNull(remitente, "remitente");
        this.fechaEnvio = Objects.requireNonNull(fechaEnvio, "fechaEnvio");
        this.hashSha256 = Objects.requireNonNull(hashSha256, "hashSha256");
        if ((canal == Canal.FORO) != (destinatario == null)) {
            throw new IllegalArgumentException("FORO no lleva destinatario y PRIVADO lo requiere");
        }
        this.destinatario = destinatario;
    }

    /** @return TEXTO o IMAGEN. */
    public abstract TipoMensaje getTipo();

    /** @return la vista del mensaje para capas superiores. */
    public MensajeDTO aDTO() {
        return switch (this) {
            case MensajeTexto t -> new MensajeDTO(id.toString(), getTipo(), canal, remitente, destinatario,
                    fechaEnvio, hashSha256, t.getNumCaracteres(), t.getNumPalabras(), null);
            case MensajeImagen i -> new MensajeDTO(id.toString(), getTipo(), canal, remitente, destinatario,
                    fechaEnvio, hashSha256, 0, 0, null);
        };
    }

    public UUID getId() {
        return id;
    }

    public Canal getCanal() {
        return canal;
    }

    public String getRemitente() {
        return remitente;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    public String getHashSha256() {
        return hashSha256;
    }
}
