package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;

import java.util.List;
import java.util.Optional;

/**
 * Consulta de mensajes, siempre desde las estructuras de datos en memoria.
 */
public interface IAlmacenMensajes {

    /**
     * Persiste el mensaje y después lo indexa en memoria (write-through).
     *
     * @param m mensaje procesado.
     * @return el mensaje con las rutas de sus archivos.
     */
    MensajeDTO agregar(Mensaje m);

    Optional<MensajeDTO> buscar(String id);

    /** @return los mensajes del foro en orden cronológico. */
    List<MensajeDTO> foro();

    /** @return la conversación privada entre {@code a} y {@code b}, en orden. */
    List<MensajeDTO> conversacion(String a, String b);

    /** @return todos los mensajes en orden de llegada. */
    List<MensajeDTO> todos();

    /** @return los bytes de una versión de la imagen, si existe. */
    Optional<byte[]> imagen(String id, TipoFiltro etapa);

    /** Carga en memoria el historial persistido. */
    void cargarHistorial();
}
