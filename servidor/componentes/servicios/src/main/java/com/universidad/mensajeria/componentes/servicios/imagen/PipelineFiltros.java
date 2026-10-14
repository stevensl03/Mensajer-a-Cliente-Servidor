package com.universidad.mensajeria.componentes.servicios.imagen;

import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cadena de filtros (Pipes &amp; Filters). Los filtros se ejecutan en el orden
 * en que se agregaron y cada uno recibe la salida del anterior.
 *
 * <p>Si un filtro falla, el procesamiento completo falla: un mensaje de imagen
 * siempre tiene la ORIGINAL y las 5 versiones (la fachada registra el error).</p>
 */
public final class PipelineFiltros {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineFiltros.class);
    private static final String FORMATO_SALIDA = "png";

    private final IGeneradorHash hash;
    private final List<IFiltroImagen> filtros = new ArrayList<>();

    /**
     * @param hash generador del hash de cada imagen producida.
     */
    public PipelineFiltros(IGeneradorHash hash) {
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /**
     * @param filtro filtro a añadir al final de la cadena.
     * @return este pipeline, para encadenar llamadas.
     * @throws IllegalArgumentException si el nombre del filtro no es un
     *                                  {@link TipoFiltro} o es {@code ORIGINAL}.
     */
    public PipelineFiltros agregar(IFiltroImagen filtro) {
        Objects.requireNonNull(filtro, "filtro");
        if (TipoFiltro.valueOf(filtro.nombre()) == TipoFiltro.ORIGINAL) {
            throw new IllegalArgumentException("ORIGINAL no es un filtro");
        }
        filtros.add(filtro);
        return this;
    }

    /** @return los filtros en orden de ejecución. */
    public List<IFiltroImagen> filtros() {
        return List.copyOf(filtros);
    }

    /**
     * Ejecuta los filtros secuencialmente.
     *
     * @param img imagen de entrada (no se modifica).
     * @return una imagen nueva (PNG) por cada filtro, en orden.
     * @throws IllegalStateException si algún filtro falla.
     */
    public List<ImagenProcesadaDTO> ejecutarSecuencial(BufferedImage img) {
        Objects.requireNonNull(img, "img");
        var resultado = new ArrayList<ImagenProcesadaDTO>(filtros.size());
        BufferedImage actual = img;
        for (IFiltroImagen filtro : filtros) {
            BufferedImage salida;
            byte[] datos;
            try {
                salida = filtro.aplicar(actual);
                datos = UtilImagen.aBytes(salida, FORMATO_SALIDA);
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Falló el filtro " + filtro.nombre(), e);
            }
            resultado.add(new ImagenProcesadaDTO(TipoFiltro.valueOf(filtro.nombre()), datos, hash.generar(datos)));
            LOG.debug("Filtro {} aplicado ({} bytes)", filtro.nombre(), datos.length);
            actual = salida;
        }
        return resultado;
    }
}
