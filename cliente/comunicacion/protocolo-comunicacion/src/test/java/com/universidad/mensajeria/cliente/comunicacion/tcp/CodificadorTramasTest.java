package com.universidad.mensajeria.cliente.comunicacion.tcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodificadorTramasTest {

    private final CodificadorTramas tramas = new CodificadorTramas(16);

    @Test
    void escribeLongitudBigEndianYContenido() throws IOException {
        var bytes = new ByteArrayOutputStream();

        tramas.escribir(new DataOutputStream(bytes), "{\"a\":\"ñ\"}".getBytes(StandardCharsets.UTF_8));

        ByteBuffer b = ByteBuffer.wrap(bytes.toByteArray());
        assertEquals(10, b.getInt());
        assertEquals(14, bytes.size());
    }

    @Test
    void idaYVuelta() throws IOException {
        var bytes = new ByteArrayOutputStream();
        var salida = new DataOutputStream(bytes);
        tramas.escribir(salida, "uno".getBytes(StandardCharsets.UTF_8));
        tramas.escribir(salida, "dos".getBytes(StandardCharsets.UTF_8));

        var entrada = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));

        assertArrayEquals("uno".getBytes(StandardCharsets.UTF_8), tramas.leer(entrada));
        assertArrayEquals("dos".getBytes(StandardCharsets.UTF_8), tramas.leer(entrada));
        assertThrows(EOFException.class, () -> tramas.leer(entrada));
    }

    @Test
    void rechazaLongitudesInvalidasAlLeer() {
        assertThrows(StreamCorruptedException.class, () -> tramas.leer(conLongitud(0)));
        assertThrows(StreamCorruptedException.class, () -> tramas.leer(conLongitud(-1)));
        assertThrows(StreamCorruptedException.class, () -> tramas.leer(conLongitud(17)));
    }

    @Test
    void unaTramaIncompletaEsFinDeFlujo() {
        byte[] b = ByteBuffer.allocate(6).putInt(5).put((byte) 'a').put((byte) 'b').array();

        assertThrows(EOFException.class, () -> tramas.leer(new DataInputStream(new ByteArrayInputStream(b))));
    }

    @Test
    void rechazaContenidoVacioOGrandeSinEscribirNada() {
        var bytes = new ByteArrayOutputStream();
        var salida = new DataOutputStream(bytes);

        assertThrows(IllegalArgumentException.class, () -> tramas.escribir(salida, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> tramas.escribir(salida, new byte[17]));
        assertEquals(0, bytes.size());
    }

    @Test
    void rechazaUnMaximoNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new CodificadorTramas(0));
    }

    private static DataInputStream conLongitud(int n) {
        return new DataInputStream(new ByteArrayInputStream(ByteBuffer.allocate(4).putInt(n).array()));
    }
}
