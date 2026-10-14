package com.universidad.mensajeria.dto;

/**
 * Etapas del pipeline de imagen, en orden de ejecución. {@code ORIGINAL} es la
 * imagen recibida y no corresponde a ningún filtro.
 */
public enum TipoFiltro {
    ORIGINAL,
    ESCALA_GRISES,
    SEPIA,
    ROTAR_180,
    BRILLO,
    REDUCCION_TAMANO
}
