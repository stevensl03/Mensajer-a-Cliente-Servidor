package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.MensajeDTO;

import java.util.List;

/**
 * Tablas {@code mensajes}, {@code mensajes_texto} e {@code imagenes_mensaje}.
 */
public interface IRepositorioMensajes {

    /** @param m mensaje con sus imágenes; se guarda en una sola transacción. */
    void guardar(MensajeDTO m);

    /** @return todos los mensajes guardados. */
    List<MensajeDTO> listarTodos();
}
