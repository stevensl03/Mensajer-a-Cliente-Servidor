package com.universidad.mensajeria.presentacion;

import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.EscuchaMensajes;
import com.universidad.mensajeria.fachada.IFachadaDeServicios;

import java.util.List;

/**
 * Vista de escritorio opcional con interfaz gráfica (componente
 * "VistaEscritorio", variante Bonus). Depende solo de
 * {@link IFachadaDeServicios}.
 */
public class VistaEscritorio implements EscuchaMensajes {

    private IFachadaDeServicios fachada;

    /**
     * Inicializa la vista con la fachada.
     *
     * @param fachada fachada del servidor.
     */
    public void iniciar(IFachadaDeServicios fachada) {
        this.fachada = fachada;
        fachada.registrarEscucha(this);
    }

    /** Muestra la ventana con la actividad del servidor. */
    public void mostrar() {
        // TODO: Swing/JavaFX: panel de conexiones y mensajes.
    }

    @Override
    public void alRecibirMensaje(MensajeDTO mensaje) {
        // TODO: actualizar la tabla de mensajes.
    }

    /** @return las conexiones activas, delegando en la fachada. */
    public List<SesionDTO> conexionesActivas() {
        return fachada.conexionesActivas();
    }

    /** @return los informes generados, delegando en la fachada. */
    public List<InformeDTO> generarInformes() {
        return fachada.generarInformes(null);
    }
}
