package com.universidad.mensajeria.componentes.filtros;

import java.awt.image.BufferedImage;

/**
 * Escala de grises por luminancia (0.299 R + 0.587 G + 0.114 B).
 */
public final class FiltroEscalaGrises implements IFiltroImagen {

    @Override
    public String nombre() {
        return "ESCALA_GRISES";
    }

    @Override
    public BufferedImage aplicar(BufferedImage img) {
        return Pixeles.mapear(img, p -> {
            long gris = Math.round(0.299 * Pixeles.rojo(p) + 0.587 * Pixeles.verde(p) + 0.114 * Pixeles.azul(p));
            return Pixeles.argb(Pixeles.alfa(p), gris, gris, gris);
        });
    }
}
