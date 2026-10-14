package com.universidad.mensajeria.fachada;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;

import java.util.List;

/**
 * Observer que implementan las vistas del servidor. Se invoca desde hilos de
 * fondo: cada vista pasa a su hilo de UI.
 */
public interface IObservadorServidor {

    /** @param m mensaje procesado. */
    void onMensaje(MensajeDTO m);

    /** @param e evento registrado en el log. */
    void onEvento(EventoLogDTO e);

    /** @param l sesiones activas después del cambio. */
    void onSesiones(List<SesionDTO> l);
}
