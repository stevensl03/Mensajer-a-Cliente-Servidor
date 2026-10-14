package com.universidad.mensajeria.componentes.servicios.imagen;

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
        int[] pixeles = leer(origen);
        for (int i = 0; i < pixeles.length; i++) {
            pixeles[i] = transformacion.applyAsInt(pixeles[i]);
        }
        return escribir(pixeles, ancho, alto);
    }

    /** @return todos los píxeles en ARGB, fila por fila (una sola lectura en bloque). */
    static int[] leer(BufferedImage origen) {
        int ancho = origen.getWidth();
        return origen.getRGB(0, 0, ancho, origen.getHeight(), null, 0, ancho);
    }

    /** @return una imagen ARGB nueva con esos píxeles. */
    static BufferedImage escribir(int[] pixeles, int ancho, int alto) {
        var destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        destino.setRGB(0, 0, ancho, alto, pixeles, 0, ancho);
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
