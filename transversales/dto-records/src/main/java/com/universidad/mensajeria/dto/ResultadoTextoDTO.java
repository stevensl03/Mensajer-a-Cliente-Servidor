package com.universidad.mensajeria.dto;

import java.util.Objects;

/**
 * Resultado del procesamiento de un texto.
 *
 * @param hash          hash SHA-256 del texto (UTF-8).
 * @param numCaracteres número de caracteres.
 * @param numPalabras   número de palabras.
 */
public record ResultadoTextoDTO(String hash, int numCaracteres, int numPalabras) {

    public ResultadoTextoDTO {
        Objects.requireNonNull(hash, "hash");
    }
}
