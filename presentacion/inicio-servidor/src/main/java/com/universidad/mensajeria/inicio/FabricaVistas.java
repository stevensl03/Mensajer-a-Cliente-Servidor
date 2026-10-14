package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.fachada.IFachadaServidor;

/**
 * Crea la vista configurada (Factory): VistaConsola o VentanaPrincipal.
 */
public final class FabricaVistas {

    private FabricaVistas() {
    }

    /**
     * @param t tipo de vista.
     * @param f fachada que usará la vista.
     * @return la vista, sin iniciar.
     */
    public static IVista crear(TipoVista t, IFachadaServidor f) {
        throw new UnsupportedOperationException("Fase 7");
    }
}
