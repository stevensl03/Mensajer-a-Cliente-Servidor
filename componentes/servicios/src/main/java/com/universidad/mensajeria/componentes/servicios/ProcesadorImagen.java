package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.UtilImagen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Procesa los mensajes de imagen: SHA-256 de la original y pipeline de filtros.
 */
public final class ProcesadorImagen {

    private final IGeneradorHash hash;
    private final PipelineFiltros pipeline;

    public ProcesadorImagen(IGeneradorHash hash, PipelineFiltros pipeline) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    /**
     * @param datos  bytes de la imagen original.
     * @param nombre nombre del archivo recibido (informativo; no se usa aún).
     * @return la original, tal como llegó, seguida de las imágenes filtradas.
     * @throws IOException si los bytes no son una imagen soportada.
     */
    public ResultadoImagenDTO procesar(byte[] datos, String nombre) throws IOException {
        Objects.requireNonNull(datos, "datos");
        var original = UtilImagen.desdeBytes(datos);
        String hashOriginal = hash.generar(datos);

        var imagenes = new ArrayList<ImagenProcesadaDTO>();
        imagenes.add(new ImagenProcesadaDTO(TipoFiltro.ORIGINAL, datos, hashOriginal));
        imagenes.addAll(pipeline.ejecutarSecuencial(original));
        return new ResultadoImagenDTO(hashOriginal, imagenes);
    }
}
