package com.universidad.mensajeria.persistencia;

import java.nio.file.Path;

/**
 * Directorio donde se guarda toda la información enviada por los usuarios.
 */
public interface IAlmacenamientoArchivos {

    /**
     * @param subdir subdirectorio ({@code textos}, {@code originales} o {@code filtradas}).
     * @param nombre nombre del archivo (sin separadores de ruta).
     * @param datos  contenido.
     * @return la ruta del archivo escrito.
     * @throws java.io.UncheckedIOException si no se puede escribir.
     */
    Path guardar(String subdir, String nombre, byte[] datos);

    /**
     * @param ruta ruta devuelta por {@link #guardar}.
     * @return el contenido del archivo.
     * @throws java.io.UncheckedIOException si no se puede leer.
     */
    byte[] leer(String ruta);

    /**
     * Borra un archivo escrito con {@link #guardar}. No falla si ya no existe.
     * AlmacenarInformacion la usa cuando falla la transacción del mensaje.
     *
     * @param ruta ruta devuelta por {@link #guardar}.
     */
    void borrar(String ruta);
}
