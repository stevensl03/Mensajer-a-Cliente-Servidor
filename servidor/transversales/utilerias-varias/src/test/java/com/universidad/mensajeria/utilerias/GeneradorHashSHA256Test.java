package com.universidad.mensajeria.utilerias;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneradorHashSHA256Test {

    private final IGeneradorHash hash = new GeneradorHashSHA256();

    @Test
    void vectorConocidoAbc() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                hash.generar("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void entradaVacia() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                hash.generar(new byte[0]));
    }
}
