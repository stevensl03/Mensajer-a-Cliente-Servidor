package com.universidad.mensajeria.cliente.persistencia;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Mensajes recibidos y enviados en la sesión, y las imágenes descargadas. */
public interface IRepositorioMensajesLocal {

    /** Clave de la conversación del foro. */
    String FORO = "FORO";

    /**
     * Guarda el mensaje si aún no está (por id). Un mensaje que llega por push y
     * por {@code HISTORIAL} queda una sola vez y conserva su primera sesión (R-54).
     *
     * @param m      mensaje.
     * @param sesion sesión local en la que llegó.
     * @param propio si lo envió el usuario de la sesión.
     */
    void guardar(MensajeDTO m, long sesion, boolean propio);

    /**
     * Guarda (o reemplaza) una versión de la imagen de un mensaje ya guardado.
     *
     * @throws IllegalArgumentException si el mensaje no está guardado (clave foránea, R-55).
     */
    void guardarAdjunto(String idMensaje, TipoFiltro etapa, String nombre, byte[] datos);

    /** @return los bytes de esa versión, si ya se descargó. */
    Optional<byte[]> adjunto(String idMensaje, TipoFiltro etapa);

    /**
     * @param clave {@link #FORO} o la de {@link #claveConversacion}.
     * @return los mensajes ordenados por fecha (y por id en empate), con
     *         {@code imagenes = []} (D-07).
     */
    List<MensajeDTO> conversacion(String clave);

    /** @return cuántos mensajes hay guardados (D-12). */
    long contar();

    /**
     * @param a un participante.
     * @param b el otro.
     * @return {@code menor|mayor}, la misma clave sin importar el orden.
     */
    static String claveConversacion(String a, String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * @param m mensaje.
     * @return la clave de su conversación.
     */
    static String claveConversacion(MensajeDTO m) {
        return m.canal() == Canal.FORO ? FORO : claveConversacion(m.remitente(), m.destinatario());
    }
}
