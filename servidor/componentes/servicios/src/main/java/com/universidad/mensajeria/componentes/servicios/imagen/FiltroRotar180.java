package com.universidad.mensajeria.componentes.servicios.imagen;

import java.awt.image.BufferedImage;

/**
 * Giro de 180 grados: el píxel (x, y) pasa a (ancho-1-x, alto-1-y).
 */
public final class FiltroRotar180 implements IFiltroImagen {

    @Override
    public String nombre() {
        return "ROTAR_180";
    }

    @Override
    public BufferedImage aplicar(BufferedImage img) {
        // Recorrer los píxeles al revés equivale a girar 180 grados.
        int[] pixeles = Pixeles.leer(img);
        for (int i = 0, j = pixeles.length - 1; i < j; i++, j--) {
            int tmp = pixeles[i];
            pixeles[i] = pixeles[j];
            pixeles[j] = tmp;
        }
        return Pixeles.escribir(pixeles, img.getWidth(), img.getHeight());
    }
}
