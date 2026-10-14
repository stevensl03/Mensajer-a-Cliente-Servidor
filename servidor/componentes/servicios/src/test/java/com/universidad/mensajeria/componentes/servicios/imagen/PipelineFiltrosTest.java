package com.universidad.mensajeria.componentes.servicios.imagen;

import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineFiltrosTest {

    private final IGeneradorHash hash = new GeneradorHashSHA256();

    @Test
    void procesarImagenDevuelveOriginalYCincoFiltrosEnOrden() throws IOException {
        var procesador = new ProcesadorImagen(hash, new FabricaFiltros(hash).crearPipelinePorDefecto());
        byte[] png = UtilImagen.aBytes(imagenDePrueba(), "png");

        var resultado = procesador.procesar(png, "prueba.png");

        assertEquals(List.of(TipoFiltro.values()),
                resultado.imagenes().stream().map(ImagenProcesadaDTO::etapa).toList());
        assertEquals(hash.generar(png), resultado.hash());
        assertArrayEquals(png, resultado.imagenes().getFirst().datos(), "la ORIGINAL se guarda tal como llegó");
        assertEquals(6, resultado.imagenes().stream().map(ImagenProcesadaDTO::hash).distinct().count());
        for (ImagenProcesadaDTO img : resultado.imagenes()) {
            assertEquals(hash.generar(img.datos()), img.hash());
        }
        BufferedImage reducida = UtilImagen.desdeBytes(resultado.imagenes().getLast().datos());
        assertEquals(2, reducida.getWidth());
        assertEquals(1, reducida.getHeight());
    }

    @Test
    void lafabricaUsaElOrdenDelDiseno() {
        assertEquals(List.of("ESCALA_GRISES", "SEPIA", "ROTAR_180", "BRILLO", "REDUCCION_TAMANO"),
                new FabricaFiltros(hash).crearPipelinePorDefecto().filtros().stream().map(IFiltroImagen::nombre).toList());
    }

    @Test
    void cadaFiltroRecibeLaSalidaDelAnterior() {
        var a = new FiltroEspia("ESCALA_GRISES");
        var b = new FiltroEspia("SEPIA");
        var c = new FiltroEspia("ROTAR_180");
        BufferedImage entrada = imagenDePrueba();

        new PipelineFiltros(hash).agregar(a).agregar(b).agregar(c).ejecutarSecuencial(entrada);

        assertSame(entrada, a.recibida);
        assertSame(a.producida, b.recibida);
        assertSame(b.producida, c.recibida);
    }

    @Test
    void elPipelineCompletoEsLaComposicionDeLosFiltros() throws IOException {
        BufferedImage entrada = imagenDePrueba();
        BufferedImage esperada = new FiltroRotar180().aplicar(new FiltroSepia().aplicar(new FiltroEscalaGrises().aplicar(entrada)));

        var resultado = new PipelineFiltros(hash).agregar(new FiltroEscalaGrises()).agregar(new FiltroSepia())
                .agregar(new FiltroRotar180()).ejecutarSecuencial(entrada);

        assertArrayEquals(Pixeles.leer(esperada), Pixeles.leer(UtilImagen.desdeBytes(resultado.getLast().datos())));
    }

    @Test
    void ningunFiltroModificaSuEntrada() {
        for (IFiltroImagen filtro : new FabricaFiltros(hash).crearPipelinePorDefecto().filtros()) {
            BufferedImage entrada = imagenDePrueba();
            int[] antes = Pixeles.leer(entrada);

            BufferedImage salida = filtro.aplicar(entrada);

            assertNotSame(entrada, salida, filtro.nombre());
            assertArrayEquals(antes, Pixeles.leer(entrada), filtro.nombre());
        }
    }

    @Test
    void escalaDeGrisesIgualaLosCanales() {
        for (int p : Pixeles.leer(new FiltroEscalaGrises().aplicar(imagenDePrueba()))) {
            assertEquals(Pixeles.rojo(p), Pixeles.verde(p));
            assertEquals(Pixeles.verde(p), Pixeles.azul(p));
        }
    }

    @Test
    void sepiaConLaMatrizEstandar() {
        assertEquals(0xFF64_5945, pixel(new FiltroSepia(), 0xFFFF0000));
        assertEquals(0xFFFFFFEF, pixel(new FiltroSepia(), 0xFFFFFFFF));
    }

    @Test
    void brilloMultiplicaYLimitaLosCanalesSinTocarElAlfa() {
        assertEquals(0x80_78_3C_FF, pixel(new FiltroBrillo(1.2f), 0x80_64_32_FA));
        assertEquals(0xFF_32_19_7D, pixel(new FiltroBrillo(0.5f), 0xFF_64_32_FA));
        assertThrows(IllegalArgumentException.class, () -> new FiltroBrillo(0));
    }

    @Test
    void rotar180InvierteLaImagen() {
        BufferedImage entrada = imagenDePrueba();
        BufferedImage salida = new FiltroRotar180().aplicar(entrada);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(entrada.getRGB(x, y), salida.getRGB(3 - x, 1 - y));
            }
        }
    }

    @Test
    void reduccionDeTamanoProporcional() {
        var impar = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        BufferedImage salida = new FiltroReduccionTamano().aplicar(impar);
        assertEquals(2, salida.getWidth());
        assertEquals(2, salida.getHeight());
        assertEquals(1, new FiltroReduccionTamano(0.1).aplicar(impar).getWidth(), "nunca menor que 1 píxel");
        assertThrows(IllegalArgumentException.class, () -> new FiltroReduccionTamano(1.5));
    }

    @Test
    void unFiltroQueFallaDetieneElProcesamiento() {
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
        var pipeline = new PipelineFiltros(hash).agregar(new FiltroEscalaGrises()).agregar(roto);

        var e = assertThrows(IllegalStateException.class, () -> pipeline.ejecutarSecuencial(imagenDePrueba()));
        assertEquals("Falló el filtro SEPIA", e.getMessage());
    }

    @Test
    void originalNoEsUnFiltro() {
        assertThrows(IllegalArgumentException.class, () -> new FabricaFiltros(hash).crear(TipoFiltro.ORIGINAL));
    }

    private static int pixel(IFiltroImagen filtro, int argb) {
        var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, argb);
        return filtro.aplicar(img).getRGB(0, 0);
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

    /** Registra la imagen que recibe y la que produce. */
    private static final class FiltroEspia implements IFiltroImagen {
        private final String nombre;
        BufferedImage recibida;
        BufferedImage producida;

        FiltroEspia(String nombre) {
            this.nombre = nombre;
        }

        @Override
        public String nombre() {
            return nombre;
        }

        @Override
        public BufferedImage aplicar(BufferedImage img) {
            recibida = img;
            producida = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            return producida;
        }
    }
}
