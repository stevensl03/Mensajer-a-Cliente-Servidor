package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;

import javax.swing.JFrame;
import java.util.List;
import java.util.Objects;

/**
 * Ventana Swing del servidor con tres pestañas: Monitor, Chat ADMIN e
 * Informes. Toda actualización de UI va por {@code SwingUtilities.invokeLater}.
 */
public final class VentanaPrincipal extends JFrame implements IObservadorServidor {

    private final transient IFachadaServidor fachada;

    public VentanaPrincipal(IFachadaServidor fachada) {
        super("Mensajería Académica - Servidor");
        this.fachada = Objects.requireNonNull(fachada, "fachada");
    }

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
