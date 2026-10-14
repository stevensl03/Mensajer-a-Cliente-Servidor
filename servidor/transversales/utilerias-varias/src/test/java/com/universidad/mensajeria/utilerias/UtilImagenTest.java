package com.universidad.mensajeria.utilerias;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UtilImagenTest {

    @Test
    void idaYVueltaEnPng() throws IOException {
        var imagen = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        imagen.setRGB(2, 1, 0x00FF8040);

        BufferedImage leida = UtilImagen.desdeBytes(UtilImagen.aBytes(imagen, "png"));

        assertEquals(3, leida.getWidth());
        assertEquals(2, leida.getHeight());
        assertEquals(0x00FF8040, leida.getRGB(2, 1) & 0x00FFFFFF);
    }

    @Test
    void bytesQueNoSonImagen() {
        assertThrows(IOException.class, () -> UtilImagen.desdeBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void formatoSinEscritor() {
        var imagen = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        assertThrows(IOException.class, () -> UtilImagen.aBytes(imagen, "formato-inexistente"));
    }
}
