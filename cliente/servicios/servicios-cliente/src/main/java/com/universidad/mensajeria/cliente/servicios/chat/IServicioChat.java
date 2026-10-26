package com.universidad.mensajeria.cliente.servicios.chat;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Envía y recibe mensajes; todo lo enviado y recibido en la sesión se guarda en H2. */
public interface IServicioChat {

    /**
     * Empieza a guardar: vacía el búfer de mensajes que llegaron antes de abrir la base (R-30).
     *
     * @param u           usuario de la sesión.
     * @param sesionLocal id de la sesión local.
     */
    void iniciarSesion(UsuarioDTO u, long sesionLocal);

    /** Deja de guardar (logout o desconexión) y limpia la caché y lo pendiente (D-17). */
    void cerrarSesion();

    /**
     * @param canal   FORO o PRIVADO.
     * @param destino código del destinatario (solo PRIVADO).
     * @param c       contenido; se valida con los límites del servidor (R-50).
     * @return se completa con la respuesta {@code ENCOLADO}; el mensaje aparece al llegar su
     *         {@code MENSAJE_NUEVO}. Falla con {@link IllegalArgumentException} si el contenido no
     *         es válido, o con {@code ExcepcionServidor} si el servidor lo rechaza.
     */
    CompletableFuture<Void> enviar(Canal canal, String destino, ContenidoDTO c);

    /**
     * Guarda un mensaje recibido (push o historial) en H2 y en la caché. Sin
     * sesión iniciada se guarda en un búfer.
     *
     * @param m mensaje.
     */
    void recibir(MensajeDTO m);

    /**
     * @param conversacion {@code FORO} o el código del otro usuario.
     * @return lo que ya está en la caché o en H2, ordenado por fecha. Lee H2: no
     *         se llama desde el hilo de la UI.
     */
    List<MensajeDTO> historial(String conversacion);

    /**
     * Pide {@code HISTORIAL} al servidor y guarda lo que falte (D-17).
     *
     * @param conversacion {@code FORO} o el código del otro usuario.
     * @return los mensajes que no estaban guardados, ordenados por fecha.
     */
    CompletableFuture<List<MensajeDTO>> sincronizar(String conversacion);

    /**
     * @param idMensaje mensaje de imagen.
     * @param etapa     versión pedida.
     * @return los bytes de H2 o, si no están, de {@code OBTENER_IMAGEN} (y se guardan en H2).
     */
    CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa);

    /** @return los mensajes guardados en la base local, o 0 sin sesión (D-12). Lee H2. */
    long mensajesGuardados();
}
