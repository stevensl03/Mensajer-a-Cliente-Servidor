package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.dto.EventoPushDTO;

/**
 * Callback de los eventos que el servidor envía sin que se pidan. Se invoca
 * desde el hilo lector de la conexión: no debe bloquearse por mucho tiempo.
 */
public interface IOyentePush {

    /**
     * @param e evento recibido (incluido {@code INFO_SERVIDOR}).
     */
    void onPush(EventoPushDTO e);

    /**
     * Se llama una vez por conexión perdida o cerrada, después de fallar las
     * peticiones pendientes.
     *
     * @param motivo descripción legible del cierre.
     */
    void onDesconexion(String motivo);
}
