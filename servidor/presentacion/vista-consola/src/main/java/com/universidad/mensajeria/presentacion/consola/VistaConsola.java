package com.universidad.mensajeria.presentacion.consola;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;

import java.util.List;
import java.util.Objects;

/**
 * Vista de consola del servidor: imprime la información de cada mensaje que
 * llega y atiende el menú del administrador.
 */
public final class VistaConsola implements IObservadorServidor {

    private final IFachadaServidor fachada;

    public VistaConsola(IFachadaServidor fachada) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
    }

    /** Muestra el menú y atiende al administrador hasta que elija salir. */
    public void iniciar() {
        throw new UnsupportedOperationException("Fase 7");
    }

    public void detener() {
        throw new UnsupportedOperationException("Fase 7");
    }

    @Override
    public void onMensaje(MensajeDTO m) {
        throw new UnsupportedOperationException("Fase 7");
    }

    @Override
    public void onEvento(EventoLogDTO e) {
        throw new UnsupportedOperationException("Fase 7");
    }

    @Override
    public void onSesiones(List<SesionDTO> l) {
        throw new UnsupportedOperationException("Fase 7");
    }
}
