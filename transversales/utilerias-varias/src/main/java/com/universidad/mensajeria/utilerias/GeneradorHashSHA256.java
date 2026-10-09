package com.universidad.mensajeria.utilerias;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hash SHA-256 en hexadecimal (64 caracteres). Sin estado y thread-safe: crea
 * un {@link MessageDigest} por invocación.
 */
public final class GeneradorHashSHA256 implements IGeneradorHash {

    private static final String ALGORITMO = "SHA-256";

    @Override
    public String generar(byte[] datos) {
        Objects.requireNonNull(datos, "datos");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITMO).digest(datos));
        } catch (NoSuchAlgorithmException e) {
            // Todo JDK está obligado a soportar SHA-256.
            throw new IllegalStateException(ALGORITMO + " no disponible", e);
        }
    }
}
