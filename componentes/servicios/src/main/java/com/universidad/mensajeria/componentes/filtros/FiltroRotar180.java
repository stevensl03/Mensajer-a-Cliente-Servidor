package com.universidad.mensajeria.componentes.filtros;

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
        int ancho = img.getWidth();
        int alto = img.getHeight();
        var destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                destino.setRGB(ancho - 1 - x, alto - 1 - y, img.getRGB(x, y));
            }
        }
        return destino;
    }
}
