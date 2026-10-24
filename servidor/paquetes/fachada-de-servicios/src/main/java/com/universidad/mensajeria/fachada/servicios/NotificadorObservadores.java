package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Sujeto del patrón Observer: avisa a las vistas del servidor (consola o
 * escritorio). Un observador que falla no impide avisar a los demás.
 */
public final class NotificadorObservadores {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorObservadores.class);

    private final CopyOnWriteArrayList<IObservadorServidor> observadores = new CopyOnWriteArrayList<>();

    public void suscribir(IObservadorServidor o) {
        observadores.addIfAbsent(Objects.requireNonNull(o, "o"));
    }

    /** @param m mensaje procesado (incluye la bandeja del ADMIN). */
    public void mensaje(MensajeDTO m) {
        avisar(o -> o.onMensaje(m));
    }

    /** @param e evento registrado en el log. */
    public void evento(EventoLogDTO e) {
        avisar(o -> o.onEvento(e));
    }

    /** @param l sesiones activas después de un cambio. */
    public void sesiones(List<SesionDTO> l) {
        List<SesionDTO> copia = List.copyOf(l);
        avisar(o -> o.onSesiones(copia));
    }

    private void avisar(Consumer<IObservadorServidor> aviso) {
        for (IObservadorServidor o : observadores) {
            try {
                aviso.accept(o);
            } catch (RuntimeException e) {
                LOG.warn("El observador {} falló al recibir un aviso", o.getClass().getSimpleName(), e);
            }
        }
    }
}
