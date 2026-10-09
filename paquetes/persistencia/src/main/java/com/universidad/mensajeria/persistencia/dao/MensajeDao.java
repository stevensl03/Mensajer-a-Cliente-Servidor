package com.universidad.mensajeria.persistencia.dao;

import com.universidad.mensajeria.dto.MensajeImagenDTO;
import com.universidad.mensajeria.dto.MensajeTextoDTO;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code mensajes}, {@code mensajes_texto} e {@code imagenes_mensaje}.
 *
 * <p>Los métodos de guardado son transaccionales y atómicos (C-13): el mensaje,
 * su subtipo y sus versiones se persisten todo-o-nada (sección 8.6 de la guía).
 * La imagen usa inserción por lotes para las 6 filas.</p>
 */
public interface MensajeDao {

    /**
     * Guarda un mensaje de texto y su fila en {@code mensajes_texto} en una
     * sola transacción.
     *
     * @param mensaje mensaje de texto.
     */
    void guardarTexto(MensajeTextoDTO mensaje);

    /**
     * Guarda un mensaje de imagen (1 fila en {@code mensajes} + 6 en
     * {@code imagenes_mensaje}) en una sola transacción.
     *
     * @param mensaje mensaje de imagen con sus 6 versiones.
     */
    void guardarImagen(MensajeImagenDTO mensaje);

    /**
     * @param id identificador del mensaje.
     * @return el mensaje de texto si existe.
     */
    Optional<MensajeTextoDTO> buscarTexto(String id);

    /** @return todos los mensajes de texto. */
    List<MensajeTextoDTO> listarTextos();

    /** @return todos los mensajes de imagen con sus versiones. */
    List<MensajeImagenDTO> listarImagenes();
}
