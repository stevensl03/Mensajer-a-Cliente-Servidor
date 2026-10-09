package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.Optional;

/**
 * Un cliente conectado, independiente del transporte (TCP, UDP o gRPC).
 *
 * <p>{@link #enviar} y {@link #push} no bloquean: solo encolan. Las
 * implementaciones son seguras entre hilos.</p>
 */
public interface ICanalCliente {

    /** @return identificador único de la conexión. */
    String getId();

    /** @return IP remota del cliente. */
    String getIpRemota();

    /** @return el usuario autenticado en esta conexión, si ya inició sesión. */
    Optional<UsuarioDTO> getUsuario();

    /**
     * Asocia el usuario autenticado a esta conexión.
     *
     * @param u usuario autenticado.
     */
    void asociar(UsuarioDTO u);

    /**
     * Encola la respuesta a una petición.
     *
     * @param r respuesta.
     */
    void enviar(RespuestaDTO r);

    /**
     * Encola un evento no solicitado.
     *
     * @param e evento.
     */
    void push(EventoPushDTO e);

    /** Cierra la conexión tras entregar lo ya encolado. Idempotente. */
    void cerrar();
}
