package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** El componente ensamblado por su fábrica, tal como lo usará la fachada. */
class ServiciosProcesamientoTest {

    private final IServiciosDisponibles servicios = FabricaServicios.crear(new GeneradorHashSHA256());

    @Test
    void procesaTexto() {
        var r = servicios.procesarTexto("Hola comunidad Unillanos");
        assertEquals(24, r.numCaracteres());
        assertEquals(3, r.numPalabras());
        assertEquals(64, r.hash().length());
    }

    @Test
    void procesaPngYJpeg() throws IOException {
        var img = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        for (String formato : List.of("png", "jpg")) {
            var r = servicios.procesarImagen(UtilImagen.aBytes(img, formato), "foto." + formato);
            assertEquals(List.of(TipoFiltro.values()), r.imagenes().stream().map(ImagenProcesadaDTO::etapa).toList());
            BufferedImage reducida = UtilImagen.desdeBytes(r.imagenes().getLast().datos());
            assertEquals(10, reducida.getWidth(), formato);
            assertEquals(5, reducida.getHeight(), formato);
        }
    }

    @Test
    void bytesQueNoSonImagen() {
        assertThrows(IllegalArgumentException.class, () -> servicios.procesarImagen(new byte[]{1, 2, 3}, "x.png"));
    }
}
