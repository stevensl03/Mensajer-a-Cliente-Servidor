package com.universidad.mensajeria.inicio;

/**
 * Contrato común de las vistas del servidor.
 */
public interface IVista {

    /** Arranca la vista (en consola, contiene el bucle del menú). */
    void iniciar();

    /** Cierra la vista. */
    void detener();
}
