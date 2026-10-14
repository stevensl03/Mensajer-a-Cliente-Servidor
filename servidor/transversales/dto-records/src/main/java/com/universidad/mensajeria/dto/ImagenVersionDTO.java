package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Una versión guardada de una imagen (fila de {@code imagenes_mensaje}).
 *
 * @param orden         0 a 5 (0 = original).
 * @param etapa         etapa del pipeline que la produjo.
 * @param nombreArchivo nombre del archivo en disco.
 * @param rutaArchivo   ruta en el directorio de almacenamiento.
 * @param tamanoBytes   tamaño en bytes.
 * @param hash          hash SHA-256 de la versión.
 */
public record ImagenVersionDTO(int orden, TipoFiltro etapa, String nombreArchivo,
                               String rutaArchivo, long tamanoBytes, String hash) {

    public ImagenVersionDTO {
        Objects.requireNonNull(etapa, "etapa");
        Objects.requireNonNull(nombreArchivo, "nombreArchivo");
        Objects.requireNonNull(hash, "hash");
        if (orden < 0 || orden > 5) {
            throw new IllegalArgumentException("orden fuera de 0..5: " + orden);
        }
        if (tamanoBytes < 0) {
            throw new IllegalArgumentException("tamanoBytes no puede ser negativo: " + tamanoBytes);
        }
    }
}
