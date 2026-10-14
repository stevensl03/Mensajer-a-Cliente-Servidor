package com.universidad.mensajeria.dto;

import java.util.List;
import java.util.Objects;

/**
 * Resultado del procesamiento de una imagen.
 *
 * @param hash     hash SHA-256 de la imagen original.
 * @param imagenes la original y las imágenes filtradas, en orden del pipeline.
 */
public record ResultadoImagenDTO(String hash, List<ImagenProcesadaDTO> imagenes) {

    public ResultadoImagenDTO {
        Objects.requireNonNull(imagenes, "imagenes");
        Objects.requireNonNull(hash, "hash");
        imagenes = List.copyOf(imagenes);
    }
}
