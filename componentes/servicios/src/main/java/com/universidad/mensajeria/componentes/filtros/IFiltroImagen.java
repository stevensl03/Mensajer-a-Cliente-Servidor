package com.universidad.mensajeria.componentes.filtros;

import java.awt.image.BufferedImage;

/**
 * Filtro de imagen (patrón Strategy).
 *
 * <p>Interfaz abierta: es un comportamiento extensible. El orden de ejecución
 * lo define {@code PipelineFiltros}, no el filtro.</p>
 */
public interface IFiltroImagen {

    /** @return el nombre de la etapa; coincide con un valor de {@code TipoFiltro}. */
    String nombre();

    /**
     * Aplica el filtro sin modificar la entrada.
     *
     * @param img imagen de entrada.
     * @return una imagen nueva con el filtro aplicado.
     */
    BufferedImage aplicar(BufferedImage img);
}
