package com.universidad.mensajeria.presentacion;

import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.EscuchaMensajes;
import com.universidad.mensajeria.fachada.IFachadaDeServicios;

import java.util.List;
import java.util.Scanner;

/**
 * Vista por consola (componente "VistaConsola"). Depende solo de
 * {@link IFachadaDeServicios}.
 */
public class VistaConsola implements EscuchaMensajes {

    private IFachadaDeServicios fachada;
    private Scanner scanner;

    /**
     * Inicializa la vista con la fachada.
     *
     * @param fachada fachada del servidor.
     */
    public void iniciar(IFachadaDeServicios fachada) {
        this.fachada = fachada;
        this.scanner = new Scanner(System.in);
        fachada.registrarEscucha(this);
    }

    /** Muestra el menú interactivo. */
    public void mostrar() {
        // TODO: menú: iniciar servidor, usuarios conectados, conexiones activas,
        //       cerrar conexión, difusión, informes, detener.
    }

    @Override
    public void alRecibirMensaje(MensajeDTO mensaje) {
        // TODO: imprimir en consola.
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
