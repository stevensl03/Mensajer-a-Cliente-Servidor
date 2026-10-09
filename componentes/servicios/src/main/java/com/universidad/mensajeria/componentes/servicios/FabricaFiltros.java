package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.componentes.filtros.FiltroBrillo;
import com.universidad.mensajeria.componentes.filtros.FiltroEscalaGrises;
import com.universidad.mensajeria.componentes.filtros.FiltroReduccionTamano;
import com.universidad.mensajeria.componentes.filtros.FiltroRotar180;
import com.universidad.mensajeria.componentes.filtros.FiltroSepia;
import com.universidad.mensajeria.componentes.filtros.IFiltroImagen;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

import java.util.List;
import java.util.Objects;

/**
 * Fábrica de filtros y del pipeline por defecto.
 */
public final class FabricaFiltros {

    /** Orden del pipeline exigido por los requerimientos. */
    private static final List<TipoFiltro> ORDEN_POR_DEFECTO = List.of(
            TipoFiltro.ESCALA_GRISES,
            TipoFiltro.SEPIA,
            TipoFiltro.ROTAR_180,
            TipoFiltro.BRILLO,
            TipoFiltro.REDUCCION_TAMANO);

    private final IGeneradorHash hash;

    /**
     * @param hash generador de hash que se inyecta en los pipelines creados.
     */
    public FabricaFiltros(IGeneradorHash hash) {
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /** @return el pipeline con los 5 filtros en el orden de los requerimientos. */
    public PipelineFiltros crearPipelinePorDefecto() {
        var pipeline = new PipelineFiltros(hash);
        ORDEN_POR_DEFECTO.forEach(tipo -> pipeline.agregar(crear(tipo)));
        return pipeline;
    }

    /**
     * @param tipo tipo de filtro.
     * @return una instancia nueva del filtro con sus parámetros por defecto.
     * @throws IllegalArgumentException si {@code tipo} es {@link TipoFiltro#ORIGINAL}.
     */
    public IFiltroImagen crear(TipoFiltro tipo) {
        return switch (Objects.requireNonNull(tipo, "tipo")) {
            case ESCALA_GRISES -> new FiltroEscalaGrises();
            case SEPIA -> new FiltroSepia();
            case ROTAR_180 -> new FiltroRotar180();
            case BRILLO -> new FiltroBrillo();
            case REDUCCION_TAMANO -> new FiltroReduccionTamano();
            case ORIGINAL -> throw new IllegalArgumentException("ORIGINAL no es un filtro");
        };
    }
}
