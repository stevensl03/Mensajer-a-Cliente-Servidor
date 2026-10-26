package com.universidad.mensajeria.cliente.fachada;

import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;

import java.util.List;

/**
 * Avisos de la fachada a la presentación. Llegan desde hilos de fondo: quien
 * los recibe pasa al hilo de la UI antes de tocar controles.
 */
public interface IObservadorCliente {

    /** @param m mensaje nuevo (push o refresco del historial); puede repetirse, se deduplica por id. */
    void onMensaje(MensajeDTO m);

    /** @param l todos los usuarios con su estado actual. */
    void onUsuarios(List<EstadoUsuarioDTO> l);

    /**
     * Se perdió la sesión (cerrada por el ADMIN, servidor detenido o caída de
     * red). Llega una sola vez por sesión, después de cerrar lo local. No llega
     * tras un {@code logout()} pedido por el usuario.
     *
     * @param motivo motivo legible.
     */
    void onDesconexion(String motivo);
}
