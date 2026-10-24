package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.utilerias.ISerializador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entrega los mensajes y eventos push: FORO a todas las sesiones; PRIVADO a
 * las sesiones del destinatario y del remitente. Todo mensaje llega además a
 * las vistas (si el destinatario es el ADMIN, es su bandeja).
 */
public final class EnrutadorMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(EnrutadorMensajes.class);

    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;
    private final NotificadorObservadores notificador;
    private final ISerializador json;

    public EnrutadorMensajes(IGestorConexiones conexiones, IClienteServidor clienteServidor,
                             NotificadorObservadores notificador, ISerializador json) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.notificador = Objects.requireNonNull(notificador, "notificador");
        this.json = Objects.requireNonNull(json, "json");
    }

    /**
     * Envía el push {@code MENSAJE_NUEVO} a quien corresponda y avisa a las vistas.
     *
     * @param m mensaje ya guardado.
     */
    public void entregar(MensajeDTO m) {
        EventoPushDTO e = evento(TipoEventoPush.MENSAJE_NUEVO, Map.of("mensaje", m));
        if (m.canal() == Canal.FORO) {
            difundir(e);
        } else {
            enviarA(m.destinatario(), e);
            if (!m.remitente().equals(m.destinatario())) {
                enviarA(m.remitente(), e);
            }
        }
        notificador.mensaje(m);
    }

    /** @param e evento para todas las sesiones activas. */
    public void difundir(EventoPushDTO e) {
        enviar(conexiones.canalesActivos(), e);
    }

    /**
     * @param codigo usuario destinatario (todas sus sesiones).
     * @param e      evento a enviar.
     */
    public void enviarA(String codigo, EventoPushDTO e) {
        enviar(conexiones.canalesDe(codigo), e);
    }

    /**
     * @param c canal destinatario (aunque no tenga sesión).
     * @param e evento a enviar.
     */
    public void enviarACanal(ICanalCliente c, EventoPushDTO e) {
        enviar(List.of(c), e);
    }

    /**
     * @param tipo  tipo del evento.
     * @param datos objeto que se convierte en el campo {@code datos}, o {@code null}.
     * @return el evento con la fecha actual.
     */
    public EventoPushDTO evento(TipoEventoPush tipo, Object datos) {
        return new EventoPushDTO(tipo, LocalDateTime.now(), datos == null ? null : json.aArbol(datos));
    }

    private void enviar(Collection<ICanalCliente> canales, EventoPushDTO e) {
        if (canales.isEmpty()) {
            return;
        }
        String trama = clienteServidor.serializar(e);
        for (ICanalCliente c : canales) {
            try {
                c.enviar(trama);
            } catch (RuntimeException ex) {
                // Un canal caído no debe impedir la entrega a los demás.
                LOG.warn("No se pudo enviar {} al canal {}", e.evento(), c.getId(), ex);
            }
        }
    }
}
