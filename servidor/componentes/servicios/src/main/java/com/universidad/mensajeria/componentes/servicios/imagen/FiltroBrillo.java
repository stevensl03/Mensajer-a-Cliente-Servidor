package com.universidad.mensajeria.componentes.servicios.imagen;

import java.awt.image.BufferedImage;

/**
 * Ajuste de brillo multiplicando cada canal de color por {@code factor}.
 */
public final class FiltroBrillo implements IFiltroImagen {

    public static final float FACTOR_POR_DEFECTO = 1.2f;

    private final float factor;

    public FiltroBrillo() {
        this(FACTOR_POR_DEFECTO);
    }

    /**
     * @param factor mayor que 1 aclara; entre 0 y 1 oscurece.
     */
    public FiltroBrillo(float factor) {
        if (!(factor > 0)) {
            throw new IllegalArgumentException("El factor de brillo debe ser positivo: " + factor);
        }
        this.factor = factor;
    }

    @Override
    public String nombre() {
        return "BRILLO";
    }

    @Override
    public BufferedImage aplicar(BufferedImage img) {
        return Pixeles.mapear(img, p -> Pixeles.argb(Pixeles.alfa(p),
                Math.round(Pixeles.rojo(p) * factor),
                Math.round(Pixeles.verde(p) * factor),
                Math.round(Pixeles.azul(p) * factor)));
    }
}
