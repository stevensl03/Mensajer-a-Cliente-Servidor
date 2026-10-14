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
 * <p>Tolerancia a fallos: si un filtro lanza una excepción se omite su imagen,
 * se registra el error y la cadena continúa con la última imagen válida.</p>
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
     * @return una imagen nueva (PNG) por cada filtro que terminó bien, en orden.
     */
    public List<ImagenProcesadaDTO> ejecutarSecuencial(BufferedImage img) {
        Objects.requireNonNull(img, "img");
        var resultado = new ArrayList<ImagenProcesadaDTO>(filtros.size());
        BufferedImage actual = img;
        for (IFiltroImagen filtro : filtros) {
            try {
                BufferedImage salida = filtro.aplicar(actual);
                byte[] datos = UtilImagen.aBytes(salida, FORMATO_SALIDA);
                resultado.add(new ImagenProcesadaDTO(TipoFiltro.valueOf(filtro.nombre()), datos, hash.generar(datos)));
                actual = salida;
            } catch (IOException | RuntimeException e) {
                LOG.error("Falló el filtro {}; se omite su imagen", filtro.nombre(), e);
            }
        }
        return resultado;
    }
}
