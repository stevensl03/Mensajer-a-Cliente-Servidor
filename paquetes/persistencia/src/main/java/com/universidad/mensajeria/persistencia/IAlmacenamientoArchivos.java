package com.universidad.mensajeria.persistencia;

import java.nio.file.Path;

/**
 * Directorio donde se guarda toda la información enviada por los usuarios.
 */
public interface IAlmacenamientoArchivos {

    /**
     * @param subdir subdirectorio ({@code textos}, {@code originales} o {@code filtradas}).
     * @param nombre nombre del archivo.
     * @param datos  contenido.
     * @return la ruta del archivo escrito.
     */
    Path guardar(String subdir, String nombre, byte[] datos);

    /**
     * @param ruta ruta devuelta por {@link #guardar}.
     * @return el contenido del archivo.
     */
    byte[] leer(String ruta);
}
