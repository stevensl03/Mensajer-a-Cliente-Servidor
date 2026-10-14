package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad de dominio de un mensaje (texto o imagen).
 */
public abstract sealed class Mensaje permits MensajeTexto, MensajeImagen {

    protected final String id;
    protected final Canal canal;
    protected final String remitente;
    protected final String destinatario;
    protected final LocalDateTime fecha;
    protected final String hash;

    protected Mensaje(String id, Canal canal, String remitente, String destinatario,
                      LocalDateTime fecha, String hash) {
        this.id = Objects.requireNonNull(id, "id");
        this.canal = Objects.requireNonNull(canal, "canal");
        this.remitente = Objects.requireNonNull(remitente, "remitente");
        this.fecha = Objects.requireNonNull(fecha, "fecha");
        this.hash = Objects.requireNonNull(hash, "hash");
        if ((canal == Canal.FORO) != (destinatario == null)) {
            throw new IllegalArgumentException("FORO no lleva destinatario y PRIVADO lo requiere");
        }
        this.destinatario = destinatario;
    }

    /** @return TEXTO o IMAGEN. */
    public abstract TipoMensaje tipo();

    /** @return la vista del mensaje para las capas superiores. */
    public abstract MensajeDTO aDTO();

    /**
     * @param a código de un participante.
     * @param b código del otro participante.
     * @return la clave de la conversación: {@code menor|mayor}.
     */
    public static String claveConversacion(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
