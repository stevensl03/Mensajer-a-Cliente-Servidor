package com.universidad.mensajeria.componentes.servicios.imagen;

import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.UtilImagen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Procesa los mensajes de imagen: SHA-256 de la original y pipeline de filtros.
 */
public final class ProcesadorImagen implements IProcesadorImagen {

    private final IGeneradorHash hash;
    private final PipelineFiltros pipeline;

    public ProcesadorImagen(IGeneradorHash hash, PipelineFiltros pipeline) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    @Override
    public ResultadoImagenDTO procesar(byte[] datos, String nombre) {
        Objects.requireNonNull(datos, "datos");
        BufferedImage original;
        try {
            original = UtilImagen.desdeBytes(datos);
        } catch (IOException e) {
            throw new IllegalArgumentException("La imagen " + nombre + " no es válida: " + e.getMessage(), e);
        }
        String hashOriginal = hash.generar(datos);

        var imagenes = new ArrayList<ImagenProcesadaDTO>();
        imagenes.add(new ImagenProcesadaDTO(TipoFiltro.ORIGINAL, datos, hashOriginal));
        imagenes.addAll(pipeline.ejecutarSecuencial(original));
        return new ResultadoImagenDTO(hashOriginal, imagenes);
    }
}
