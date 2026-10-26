package com.universidad.mensajeria.cliente.comunicacion.tcp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;

/**
 * Tramas {@code [int32 big-endian = N][N bytes]} con {@code 1 ≤ N ≤ maxTramaBytes},
 * igual que el servidor (servidor §7).
 */
public final class CodificadorTramas {

    private final int maxTramaBytes;

    /**
     * @param maxTramaBytes tamaño máximo del contenido de una trama.
     */
    public CodificadorTramas(int maxTramaBytes) {
        if (maxTramaBytes <= 0) {
            throw new IllegalArgumentException("maxTramaBytes debe ser mayor que 0: " + maxTramaBytes);
        }
        this.maxTramaBytes = maxTramaBytes;
    }

    /**
     * @param in flujo de entrada.
     * @return el contenido de la siguiente trama.
     * @throws StreamCorruptedException si la longitud es inválida (ya no se sabe dónde empieza la siguiente).
     * @throws java.io.EOFException     si el flujo termina antes o en medio de una trama.
     * @throws IOException              ante un error de lectura.
     */
    public byte[] leer(DataInputStream in) throws IOException {
        int longitud = in.readInt();
        if (longitud <= 0 || longitud > maxTramaBytes) {
            throw new StreamCorruptedException("Longitud de trama inválida: " + longitud);
        }
        byte[] payload = new byte[longitud];
        in.readFully(payload);
        return payload;
    }

    /**
     * Valida antes de escribir, así un contenido inválido no deja el flujo a medias.
     *
     * @param out flujo de salida.
     * @param b   contenido de la trama.
     * @throws IllegalArgumentException si {@code b} está vacío o supera el máximo.
     * @throws IOException              ante un error de escritura.
     */
    public void escribir(DataOutputStream out, byte[] b) throws IOException {
        validar(b.length);
        out.writeInt(b.length);
        out.write(b);
        out.flush();
    }

    /**
     * @param longitud longitud del contenido.
     * @throws IllegalArgumentException si no cabe en una trama.
     */
    void validar(int longitud) {
        if (longitud == 0 || longitud > maxTramaBytes) {
            throw new IllegalArgumentException("Mensaje de " + longitud + " bytes fuera de rango (máx. "
                    + maxTramaBytes + ")");
        }
    }
}
