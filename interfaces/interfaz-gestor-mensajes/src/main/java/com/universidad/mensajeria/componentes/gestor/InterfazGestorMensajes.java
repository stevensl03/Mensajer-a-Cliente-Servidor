package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.soporte.mensajes.Mensaje;

import java.util.List;
import java.util.Optional;

/**
 * Estructuras de datos en memoria (componente "InterfazGestorMensajes" del
 * diagrama). Es la caché exclusiva del sistema y la fuente de consulta rápida
 * de todos los mensajes.
 *
 * <p>Las operaciones deben ser thread-safe: hay un hilo por conexión.</p>
 */
public interface InterfazGestorMensajes {

    /**
     * Agrega un mensaje a la cola de pendientes y a los índices de consulta.
     *
     * @param mensaje mensaje a guardar.
     */
    void encolar(Mensaje mensaje);

    /**
     * Extrae el siguiente mensaje pendiente de procesar.
     *
     * @return el siguiente mensaje, o vacío si no hay pendientes.
     */
    Optional<Mensaje> siguientePendiente();

    /**
     * @param id identificador (UUID) del mensaje.
     * @return el mensaje si existe.
     */
    Optional<Mensaje> buscarPorId(String id);

    /**
     * @param codigoUsuario código de usuario.
     * @return los mensajes privados en los que participa el usuario como emisor
     *         o receptor.
     */
    List<Mensaje> listarPorUsuario(String codigoUsuario);

    /** @return todos los mensajes almacenados. */
    List<Mensaje> listarTodos();

    /**
     * @return los mensajes de difusión del foro, en orden cronológico.
     */
    List<Mensaje> listarForo();

    /**
     * Recarga la caché desde el historial persistente (Sección 7.2): se invoca
     * al arrancar para no perder el historial tras un reinicio.
     *
     * @param mensajes mensajes recuperados de MySQL.
     */
    void recargarDesdeHistorial(List<Mensaje> mensajes);
}
