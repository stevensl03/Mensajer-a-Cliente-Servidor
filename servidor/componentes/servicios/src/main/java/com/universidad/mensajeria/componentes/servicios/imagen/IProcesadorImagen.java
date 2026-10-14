package com.universidad.mensajeria.componentes.servicios.imagen;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;

/**
 * Subcomponente FiltrosImagen.
 */
public interface IProcesadorImagen {

    /**
     * @param datos  bytes de la imagen original.
     * @param nombre nombre del archivo recibido.
     * @return la ORIGINAL seguida de las 5 imágenes filtradas.
     * @throws IllegalArgumentException si los bytes no son una imagen soportada.
     */
    ResultadoImagenDTO procesar(byte[] datos, String nombre);
}
