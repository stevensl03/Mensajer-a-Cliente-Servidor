package com.universidad.mensajeria.componentes.servicios.imagen;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Reducción de tamaño proporcional con interpolación bilineal.
 */
public final class FiltroReduccionTamano implements IFiltroImagen {

    public static final double ESCALA_POR_DEFECTO = 0.5;

    private final double escala;

    public FiltroReduccionTamano() {
        this(ESCALA_POR_DEFECTO);
    }

    /**
     * @param escala factor en (0, 1]; 0.5 deja la imagen a la mitad.
     */
    public FiltroReduccionTamano(double escala) {
        if (!(escala > 0 && escala <= 1)) {
            throw new IllegalArgumentException("La escala debe estar en (0, 1]: " + escala);
        }
        this.escala = escala;
    }

    @Override
    public String nombre() {
        return "REDUCCION_TAMANO";
    }

    @Override
    public BufferedImage aplicar(BufferedImage img) {
        int ancho = Math.max(1, (int) Math.round(img.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(img.getHeight() * escala));
        var destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        var g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }
}
