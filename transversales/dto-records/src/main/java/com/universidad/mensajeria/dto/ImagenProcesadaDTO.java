package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Una imagen producida por el pipeline (o la original).
 *
 * @param filtro etapa que la produjo.
 * @param datos  bytes de la imagen (PNG para las filtradas).
 * @param hash   hash SHA-256 de {@code datos}.
 */
public record ImagenProcesadaDTO(TipoFiltro filtro, byte[] datos, String hash) {

    public ImagenProcesadaDTO {
        Objects.requireNonNull(filtro, "filtro");
        Objects.requireNonNull(hash, "hash");
        datos = Objects.requireNonNull(datos, "datos").clone();
    }

    /** @return una copia de los bytes de la imagen. */
    @Override
    public byte[] datos() {
        return datos.clone();
    }
}
