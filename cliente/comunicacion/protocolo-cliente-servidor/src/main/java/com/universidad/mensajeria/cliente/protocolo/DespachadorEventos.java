package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.dto.EventoPushDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reparte los eventos push y las desconexiones entre los oyentes (Observer).
 * Un oyente que falla no impide la entrega a los demás.
 */
public final class DespachadorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(DespachadorEventos.class);

    private final CopyOnWriteArrayList<IOyentePush> oyentes = new CopyOnWriteArrayList<>();

    /** @param o oyente nuevo (si ya estaba, no se duplica). */
    public void suscribir(IOyentePush o) {
        oyentes.addIfAbsent(Objects.requireNonNull(o, "o"));
    }

    /** @param e evento para todos los oyentes. */
    public void despachar(EventoPushDTO e) {
        for (IOyentePush o : oyentes) {
            try {
                o.onPush(e);
            } catch (RuntimeException ex) {
                LOG.error("Un oyente falló al atender {}", e.evento(), ex);
            }
        }
    }

    /** @param motivo motivo de la desconexión, para todos los oyentes. */
    public void desconexion(String motivo) {
        for (IOyentePush o : oyentes) {
            try {
                o.onDesconexion(motivo);
            } catch (RuntimeException ex) {
                LOG.error("Un oyente falló al atender la desconexión", ex);
            }
        }
    }
}
