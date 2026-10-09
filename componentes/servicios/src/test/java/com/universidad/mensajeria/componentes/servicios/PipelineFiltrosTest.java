package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.componentes.filtros.FiltroEscalaGrises;
import com.universidad.mensajeria.componentes.filtros.FiltroRotar180;
import com.universidad.mensajeria.componentes.filtros.IFiltroImagen;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineFiltrosTest {

    private final IGeneradorHash hash = new GeneradorHashSHA256();

    @Test
    void procesarImagenDevuelveOriginalYCincoFiltrosEnOrden() throws IOException {
        var servicios = new ProcesadorImagen(hash, new FabricaFiltros(hash).crearPipelinePorDefecto());
        byte[] png = UtilImagen.aBytes(imagenDePrueba(), "png");

        var resultado = servicios.procesar(png, "prueba.png");

        assertEquals(List.of(TipoFiltro.values()),
                resultado.imagenes().stream().map(ImagenProcesadaDTO::filtro).toList());
        assertEquals(hash.generar(png), resultado.hash());
        assertEquals(6, resultado.imagenes().stream().map(ImagenProcesadaDTO::hash).distinct().count());
        for (ImagenProcesadaDTO img : resultado.imagenes()) {
            assertEquals(hash.generar(img.datos()), img.hash());
        }
        BufferedImage reducida = UtilImagen.desdeBytes(resultado.imagenes().getLast().datos());
        assertEquals(2, reducida.getWidth());
        assertEquals(1, reducida.getHeight());
    }

    @Test
    void escalaDeGrisesIgualaLosCanales() throws IOException {
        var salida = ejecutarUnico(new FiltroEscalaGrises());
        for (int y = 0; y < salida.getHeight(); y++) {
            for (int x = 0; x < salida.getWidth(); x++) {
                int p = salida.getRGB(x, y);
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                assertEquals(r, g);
                assertEquals(g, b);
            }
        }
    }

    @Test
    void rotar180MueveLaEsquina() throws IOException {
        var entrada = imagenDePrueba();
        var salida = ejecutarUnico(new FiltroRotar180());
        assertEquals(entrada.getRGB(0, 0), salida.getRGB(entrada.getWidth() - 1, entrada.getHeight() - 1));
    }

    @Test
    void unFiltroQueFallaNoDetieneLaCadena() {
        IFiltroImagen roto = new IFiltroImagen() {
            @Override
            public String nombre() {
                return "SEPIA";
            }

            @Override
            public BufferedImage aplicar(BufferedImage img) {
                throw new IllegalStateException("fallo simulado");
            }
        };
        var pipeline = new PipelineFiltros(hash)
                .agregar(new FiltroEscalaGrises())
                .agregar(roto)
                .agregar(new FiltroRotar180());

        var resultado = pipeline.ejecutarSecuencial(imagenDePrueba());

        assertEquals(List.of(TipoFiltro.ESCALA_GRISES, TipoFiltro.ROTAR_180),
                resultado.stream().map(ImagenProcesadaDTO::filtro).toList());
    }

    @Test
    void originalNoEsUnFiltro() {
        assertThrows(IllegalArgumentException.class, () -> new FabricaFiltros(hash).crear(TipoFiltro.ORIGINAL));
    }

    private BufferedImage ejecutarUnico(IFiltroImagen filtro) throws IOException {
        var resultado = new PipelineFiltros(hash).agregar(filtro).ejecutarSecuencial(imagenDePrueba());
        return UtilImagen.desdeBytes(resultado.getFirst().datos());
    }

    /** Imagen de 4x2 con colores distintos en cada píxel. */
    private static BufferedImage imagenDePrueba() {
        var img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        int[] colores = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
                0xFF00FFFF, 0xFFFF00FF, 0xFF808080, 0xFF102030};
        for (int i = 0; i < colores.length; i++) {
            img.setRGB(i % 4, i / 4, colores[i]);
        }
        return img;
    }
}
