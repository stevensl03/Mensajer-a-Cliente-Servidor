package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;

/**
 * Composition Root: único punto que conoce clases concretas. Carga la
 * configuración, crea cada módulo con su fábrica, inyecta por constructor y
 * entrega {@link IFachadaServidor} a la vista elegida.
 */
public final class ServidorApp {

    private ServidorApp() {
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException("Fase 7");
    }

    private static IFachadaServidor ensamblar(ConfiguracionServidor cfg) {
        throw new UnsupportedOperationException("Fase 7");
    }

    private static void registrarApagado(IFachadaServidor f, IVista v) {
        throw new UnsupportedOperationException("Fase 7");
    }
}
