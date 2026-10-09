package com.universidad.mensajeria.componentes.protocolo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodificadorTramasTest {

    private static final int MAX = 64;
    private final CodificadorTramas tramas = new CodificadorTramas(MAX);

    @Test
    void dosTramasSeguidasSeLeenPorSeparado() throws IOException {
        var buffer = new ByteArrayOutputStream();
        var out = new DataOutputStream(buffer);
        byte[] a = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] b = "{\"b\":2}".getBytes(StandardCharsets.UTF_8);
        tramas.escribir(out, a);
        tramas.escribir(out, b);

        var in = entrada(buffer.toByteArray());
        assertArrayEquals(a, tramas.leer(in));
        assertArrayEquals(b, tramas.leer(in));
        assertThrows(EOFException.class, () -> tramas.leer(in));
    }

    @Test
    void tramaTruncada() throws IOException {
        var buffer = new ByteArrayOutputStream();
        var out = new DataOutputStream(buffer);
        out.writeInt(10);
        out.write(new byte[3]);

        assertThrows(EOFException.class, () -> tramas.leer(entrada(buffer.toByteArray())));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, MAX + 1, Integer.MAX_VALUE})
    void longitudInvalida(int longitud) throws IOException {
        var buffer = new ByteArrayOutputStream();
        new DataOutputStream(buffer).writeInt(longitud);

        assertThrows(ProtocoloException.class, () -> tramas.leer(entrada(buffer.toByteArray())));
    }

    @Test
    void noEscribePayloadsFueraDeRango() {
        var out = new DataOutputStream(new ByteArrayOutputStream());
        assertThrows(ProtocoloException.class, () -> tramas.escribir(out, new byte[0]));
        assertThrows(ProtocoloException.class, () -> tramas.escribir(out, new byte[MAX + 1]));
    }

    private static DataInputStream entrada(byte[] datos) {
        return new DataInputStream(new ByteArrayInputStream(datos));
    }
}
