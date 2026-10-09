package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Procesa los mensajes de texto: SHA-256, caracteres y palabras.
 */
public final class ProcesadorTexto {

    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private final IGeneradorHash hash;

    public ProcesadorTexto(IGeneradorHash hash) {
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /**
     * @param t texto recibido; {@code null} se trata como vacío.
     * @return hash del texto en UTF-8, caracteres y palabras.
     */
    public ResultadoTextoDTO procesar(String t) {
        String texto = t == null ? "" : t;
        return new ResultadoTextoDTO(hash.generar(texto.getBytes(StandardCharsets.UTF_8)),
                contarCaracteres(texto), contarPalabras(texto));
    }

    /** Cuenta puntos de código: un emoji cuenta como un carácter. */
    private static int contarCaracteres(String texto) {
        return texto.codePointCount(0, texto.length());
    }

    /** Cuenta secuencias separadas por espacios en blanco. */
    private static int contarPalabras(String texto) {
        String limpio = texto.strip();
        return limpio.isEmpty() ? 0 : ESPACIOS.split(limpio).length;
    }
}
