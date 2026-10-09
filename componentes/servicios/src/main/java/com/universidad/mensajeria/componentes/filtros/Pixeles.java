package com.universidad.mensajeria.componentes.filtros;

import java.awt.image.BufferedImage;
import java.util.function.IntUnaryOperator;

/**
 * Ayudas internas para los filtros que transforman píxel a píxel (ARGB).
 */
final class Pixeles {

    private Pixeles() {
    }

    /** Crea una imagen ARGB nueva aplicando {@code transformacion} a cada píxel. */
    static BufferedImage mapear(BufferedImage origen, IntUnaryOperator transformacion) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        var destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                destino.setRGB(x, y, transformacion.applyAsInt(origen.getRGB(x, y)));
            }
        }
        return destino;
    }

    static int alfa(int p) {
        return p >>> 24;
    }

    static int rojo(int p) {
        return (p >> 16) & 0xFF;
    }

    static int verde(int p) {
        return (p >> 8) & 0xFF;
    }

    static int azul(int p) {
        return p & 0xFF;
    }

    /** Compone un píxel ARGB limitando cada canal de color a [0, 255]. */
    static int argb(int a, long r, long g, long b) {
        return (a << 24) | (limitar(r) << 16) | (limitar(g) << 8) | limitar(b);
    }

    private static int limitar(long valor) {
        return (int) Math.max(0, Math.min(255, valor));
    }
}
