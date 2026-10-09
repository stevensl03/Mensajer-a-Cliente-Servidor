package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.gestor.InterfazGestorMensajes;
import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.EscuchaMensajes;
import com.universidad.mensajeria.fachada.IFachadaDeServicios;
import com.universidad.mensajeria.soporte.almacenar.AlmacenarInformacion;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.LogDeEventos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementación de la fachada. Recibe los componentes ya construidos e
 * inyectados; las peticiones de los clientes las atiende el protocolo, no la
 * fachada.
 *
 * <p>En la fase de fachada se renombra a {@code FachadaServidor} según el
 * diagrama.</p>
 */
public class FachadaDeServicios implements IFachadaDeServicios {

    private final IProtocoloComunicacion protocolo;
    private final IUsuariosDisponibles usuarios;
    private final IServiciosDisponibles servicios;
    private final InterfazGestorMensajes gestor;
    private final IGestorConexiones conexiones;
    private final LogDeEventos log;
    private final AlmacenarInformacion almacenar;
    private final int puerto;

    private final List<EscuchaMensajes> escuchas = new CopyOnWriteArrayList<>();

    /**
     * @param protocolo  transporte de red.
     * @param usuarios   componente de usuarios.
     * @param servicios  componente de servicios.
     * @param gestor     componente gestor de mensajes.
     * @param conexiones gestor de conexiones activas.
     * @param log        registro de eventos.
     * @param almacenar  intermediario de persistencia.
     * @param puerto     puerto de escucha.
     */
    public FachadaDeServicios(IProtocoloComunicacion protocolo,
                              IUsuariosDisponibles usuarios,
                              IServiciosDisponibles servicios,
                              InterfazGestorMensajes gestor,
                              IGestorConexiones conexiones,
                              LogDeEventos log,
                              AlmacenarInformacion almacenar,
                              int puerto) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.usuarios = usuarios;
        this.servicios = servicios;
        this.gestor = gestor;
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.log = log;
        this.almacenar = almacenar;
        this.puerto = puerto;
    }

    @Override
    public void iniciar() {
        try {
            protocolo.iniciar(puerto);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo iniciar el servidor en el puerto " + puerto, e);
        }
        // TODO (persistencia): conectar la BD y recargar el historial.
    }

    @Override
    public void detener() {
        protocolo.detener();
    }

    @Override
    public void registrarEscucha(EscuchaMensajes escucha) {
        escuchas.add(escucha);
    }

    @Override
    public void enviarDifusion(String texto, String codigoRemitente) {
        // TODO (fase 3): publicar en el foro a través del gestor de mensajes.
    }

    @Override
    public List<String> usuariosConectados() {
        return conexiones.resumen().stream()
                .map(SesionDTO::codigo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<SesionDTO> conexionesActivas() {
        return conexiones.resumen();
    }

    @Override
    public void cerrarConexion(String idConexion) {
        conexiones.cerrar(idConexion);
    }

    @Override
    public void cerrarSesionesDe(String codigoUsuario) {
        conexiones.sesionesDe(codigoUsuario).stream()
                .map(ICanalCliente::getId)
                .forEach(conexiones::cerrar);
    }

    @Override
    public List<InformeDTO> generarInformes(String generadoPor) {
        // TODO (fase de informes)
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}
