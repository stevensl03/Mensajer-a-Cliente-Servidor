package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.ILogEventos;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Clase principal de FachadaDeServicios (Facade): única puerta de entrada de
 * las vistas. Orquesta componentes y paquetes; no contiene lógica de
 * transporte, JSON ni SQL.
 */
public final class FachadaServidor implements IFachadaServidor {

    private final IProtocoloComunicacion protocolo;
    private final IClienteServidor clienteServidor;
    private final IServiciosDisponibles servicios;
    private final IUsuariosDisponibles usuarios;
    private final IGestorMensajes gestorMensajes;
    private final IAlmacenMensajes mensajes;
    private final FabricaMensajes fabricaMensajes;
    private final ILogEventos log;
    private final IGestorConexiones conexiones;
    private final ISerializador json;
    private final ConfiguracionServidor cfg;

    public FachadaServidor(IProtocoloComunicacion protocolo, IClienteServidor clienteServidor,
                           IServiciosDisponibles servicios, IUsuariosDisponibles usuarios,
                           IGestorMensajes gestorMensajes, IAlmacenMensajes mensajes,
                           FabricaMensajes fabricaMensajes, ILogEventos log,
                           IGestorConexiones conexiones, ISerializador json, ConfiguracionServidor cfg) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.servicios = Objects.requireNonNull(servicios, "servicios");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.gestorMensajes = Objects.requireNonNull(gestorMensajes, "gestorMensajes");
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.fabricaMensajes = Objects.requireNonNull(fabricaMensajes, "fabricaMensajes");
        this.log = Objects.requireNonNull(log, "log");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.json = Objects.requireNonNull(json, "json");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    @Override
    public void iniciarServidor() {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public void detenerServidor() {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public void publicarEnForo(ContenidoDTO c) {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public void enviarPrivado(String destino, ContenidoDTO c) {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public String informe(TipoInforme t, Map<String, String> filtros) {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public List<SesionDTO> listarSesiones() {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public List<EstadoUsuarioDTO> listarUsuarios() {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public void cerrarSesion(String idSesion) {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public void suscribir(IObservadorServidor o) {
        throw new UnsupportedOperationException("Fase 6");
    }

    @Override
    public String getIpServidor() {
        return protocolo.getIpServidor();
    }
}
