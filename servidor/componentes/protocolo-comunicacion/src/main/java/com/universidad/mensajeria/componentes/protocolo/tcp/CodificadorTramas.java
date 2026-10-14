package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.componentes.protocolo.ProtocoloException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Delimita los mensajes sobre TCP: {@code [int32 longitud big-endian][payload]}.
 *
 * <p>TCP es un flujo de bytes; sin este prefijo dos mensajes pueden llegar
 * pegados o uno partido. El tope de longitud evita reservar memoria absurda
 * ante una trama corrupta o maliciosa.</p>
 */
public final class CodificadorTramas {

    private final int maxTramaBytes;

    /**
     * @param maxTramaBytes longitud máxima aceptada del payload.
     */
    public CodificadorTramas(int maxTramaBytes) {
        if (maxTramaBytes <= 0) {
            throw new IllegalArgumentException("maxTramaBytes debe ser mayor que 0: " + maxTramaBytes);
        }
        this.maxTramaBytes = maxTramaBytes;
    }

    /**
     * @param in flujo de entrada.
     * @return el payload de la siguiente trama.
     * @throws java.io.EOFException si el flujo termina (también a mitad de trama).
     * @throws ProtocoloException   si la longitud es inválida.
     * @throws IOException          si falla la lectura.
     */
    public byte[] leer(DataInputStream in) throws IOException {
        int longitud = in.readInt();
        if (longitud <= 0 || longitud > maxTramaBytes) {
            throw new ProtocoloException("Longitud de trama inválida: " + longitud);
        }
        byte[] payload = new byte[longitud];
        in.readFully(payload);
        return payload;
    }

    /**
     * Escribe una trama completa y vacía el búfer.
     *
     * @param out flujo de salida.
     * @param b   payload.
     * @throws ProtocoloException si el payload está vacío o supera el máximo.
     * @throws IOException        si falla la escritura.
     */
    public void escribir(DataOutputStream out, byte[] b) throws IOException {
        if (b.length == 0 || b.length > maxTramaBytes) {
            throw new ProtocoloException("Payload de " + b.length + " bytes fuera de rango (máx. " + maxTramaBytes + ")");
        }
        out.writeInt(b.length);
        out.write(b);
        out.flush();
    }
}
