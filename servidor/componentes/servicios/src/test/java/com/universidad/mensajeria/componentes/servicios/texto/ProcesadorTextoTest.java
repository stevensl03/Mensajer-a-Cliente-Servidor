package com.universidad.mensajeria.componentes.servicios.texto;

import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProcesadorTextoTest {

    private final GeneradorHashSHA256 hash = new GeneradorHashSHA256();
    private final ProcesadorTexto procesador = new ProcesadorTexto(hash);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "hola mundo|10|2",
            "'  varios   espacios\tentre  palabras '|36|4",
            "una|3|1",
            "''|0|0",
            "'   '|3|0",
            "canción ñandú|13|2",
            "Él está aquí|12|3"
    })
    void cuentaCaracteresYPalabras(String texto, int caracteres, int palabras) {
        var resultado = procesador.procesar(texto);
        assertEquals(caracteres, resultado.numCaracteres());
        assertEquals(palabras, resultado.numPalabras());
    }

    @Test
    void saltosDeLineaSeparanPalabrasYCuentanComoCaracteres() {
        var resultado = procesador.procesar("línea uno\nlínea dos");
        assertEquals(19, resultado.numCaracteres());
        assertEquals(4, resultado.numPalabras());

        var conRetornos = procesador.procesar("\n\nhola\r\n\tmundo\n");
        assertEquals(15, conRetornos.numCaracteres());
        assertEquals(2, conRetornos.numPalabras());
    }

    @Test
    void unEmojiCuentaComoUnCaracter() {
        assertEquals(6, procesador.procesar("hola 😀").numCaracteres());
    }

    @Test
    void nuloSeTrataComoVacio() {
        var resultado = procesador.procesar(null);
        assertEquals(0, resultado.numCaracteres());
        assertEquals(0, resultado.numPalabras());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", resultado.hash());
    }

    @Test
    void hashSobreUtf8() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                procesador.procesar("abc").hash());
        String conTildes = "canción ñandú";
        assertEquals(hash.generar(conTildes.getBytes(StandardCharsets.UTF_8)), procesador.procesar(conTildes).hash());
        assertNotEquals(hash.generar(conTildes.getBytes(StandardCharsets.ISO_8859_1)),
                procesador.procesar(conTildes).hash());
    }
}
