package com.universidad.mensajeria.utilerias;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Conversión de imágenes entre bytes y {@link BufferedImage} con {@code ImageIO}.
 */
public final class UtilImagen {

    private UtilImagen() {
    }

    /**
     * @param imagen  imagen a serializar.
     * @param formato formato destino (por ejemplo {@code png}).
     * @return los bytes de la imagen.
     * @throws IOException si no hay escritor para el formato o falla la escritura.
     */
    public static byte[] aBytes(BufferedImage imagen, String formato) throws IOException {
        Objects.requireNonNull(imagen, "imagen");
        Objects.requireNonNull(formato, "formato");
        var salida = new ByteArrayOutputStream();
        if (!ImageIO.write(imagen, formato, salida)) {
            throw new IOException("No hay escritor de imágenes para el formato " + formato);
        }
        return salida.toByteArray();
    }

    /**
     * @param datos bytes de una imagen.
     * @return la imagen decodificada.
     * @throws IOException si los bytes no son una imagen en un formato soportado.
     */
    public static BufferedImage desdeBytes(byte[] datos) throws IOException {
        Objects.requireNonNull(datos, "datos");
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(datos));
        if (imagen == null) {
            throw new IOException("Los datos no corresponden a una imagen soportada");
        }
        return imagen;
    }
}
