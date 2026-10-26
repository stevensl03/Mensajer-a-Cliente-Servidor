package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.presentacion.consola.VistaConsola;
import com.universidad.mensajeria.presentacion.escritorio.VentanaPrincipal;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.Objects;

/**
 * Crea la vista configurada (Factory): VistaConsola o VentanaPrincipal. Las
 * vistas no dependen de {@code inicio-servidor}, así que se envuelven en un
 * adaptador {@link IVista}; además se suscriben a la fachada como observadores.
 */
public final class FabricaVistas {

    private FabricaVistas() {
    }

    /**
     * @param t tipo de vista.
     * @param f    fachada que usará la vista.
     * @param json serializador del Composition Root; la vista de escritorio lee con él el JSON de los informes.
     * @return la vista, sin iniciar.
     */
    public static IVista crear(TipoVista t, IFachadaServidor f, ISerializador json) {
        Objects.requireNonNull(t, "t");
        Objects.requireNonNull(f, "f");
        Objects.requireNonNull(json, "json");
        return switch (t) {
            case CONSOLA -> {
                var consola = new VistaConsola(f);
                f.suscribir(consola);
                yield adaptar(consola::iniciar, consola::detener);
            }
            case ESCRITORIO -> {
                var ventana = new VentanaPrincipal(f, json);
                f.suscribir(ventana);
                yield adaptar(ventana::iniciar, ventana::detener);
            }
        };
    }

    private static IVista adaptar(Runnable iniciar, Runnable detener) {
        return new IVista() {
            @Override
            public void iniciar() {
                iniciar.run();
            }

            @Override
            public void detener() {
                detener.run();
            }
        };
    }
}
