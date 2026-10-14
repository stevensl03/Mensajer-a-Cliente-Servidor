package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.componentes.servicios.imagen.FabricaFiltros;
import com.universidad.mensajeria.componentes.servicios.imagen.ProcesadorImagen;
import com.universidad.mensajeria.componentes.servicios.texto.ProcesadorTexto;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

/**
 * Ensambla los dos subcomponentes de Servicios (Factory).
 */
public final class FabricaServicios {

    private FabricaServicios() {
    }

    /**
     * @param hash generador de hash.
     * @return el componente Servicios listo para usar.
     */
    public static IServiciosDisponibles crear(IGeneradorHash hash) {
        var imagen = new ProcesadorImagen(hash, new FabricaFiltros(hash).crearPipelinePorDefecto());
        return new ServiciosProcesamiento(new ProcesadorTexto(hash), imagen);
    }
}
