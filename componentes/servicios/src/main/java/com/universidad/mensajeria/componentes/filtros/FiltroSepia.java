package com.universidad.mensajeria.componentes.filtros;

import java.awt.image.BufferedImage;

/**
 * Tono sepia con la matriz estándar.
 */
public final class FiltroSepia implements IFiltroImagen {

    @Override
    public String nombre() {
        return "SEPIA";
    }

    @Override
    public BufferedImage aplicar(BufferedImage img) {
        return Pixeles.mapear(img, p -> {
            int r = Pixeles.rojo(p);
            int g = Pixeles.verde(p);
            int b = Pixeles.azul(p);
            return Pixeles.argb(Pixeles.alfa(p),
                    Math.round(0.393 * r + 0.769 * g + 0.189 * b),
                    Math.round(0.349 * r + 0.686 * g + 0.168 * b),
                    Math.round(0.272 * r + 0.534 * g + 0.131 * b));
        });
    }
}
