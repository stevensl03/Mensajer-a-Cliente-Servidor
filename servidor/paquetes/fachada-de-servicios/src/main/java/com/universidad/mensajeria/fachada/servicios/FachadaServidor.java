package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.fachada.servicios.comandos.DespachadorComandos;
import com.universidad.mensajeria.fachada.servicios.comandos.FabricaManejadoresComando;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorListarUsuarios;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.ILogEventos;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.ISerializador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Clase principal de FachadaDeServicios (Facade): única puerta de entrada de
 * las vistas. Orquesta componentes y paquetes; no contiene lógica de
 * transporte, JSON ni SQL.
 */
public final class FachadaServidor implements IFachadaServidor {

    private static final Logger LOG = LoggerFactory.getLogger(FachadaServidor.class);

    private final IProtocoloComunicacion protocolo;
    private final IGestorMensajes gestorMensajes;
    private final IUsuariosDisponibles usuarios;
    private final IAlmacenMensajes mensajes;
    private final IGestorConexiones conexiones;
    private final ISerializador json;
    private final ConfiguracionServidor cfg;
    private final NotificadorObservadores notificador;
    private final EnrutadorMensajes enrutador;
    private final ControlSesiones sesiones;
    private final GeneradorInformes informes;
    private final ProcesadorMensajes procesador;
    private final ReceptorConexiones receptor;
    private final ManejadorListarUsuarios listarUsuarios;
    private final AtomicBoolean detenido = new AtomicBoolean();

    public FachadaServidor(IProtocoloComunicacion protocolo, IClienteServidor clienteServidor,
                           IServiciosDisponibles servicios, IUsuariosDisponibles usuarios,
                           IGestorMensajes gestorMensajes, IAlmacenMensajes mensajes,
                           FabricaMensajes fabricaMensajes, ILogEventos log,
                           IGestorConexiones conexiones, ISerializador json, ConfiguracionServidor cfg) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.gestorMensajes = Objects.requireNonNull(gestorMensajes, "gestorMensajes");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.json = Objects.requireNonNull(json, "json");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(clienteServidor, "clienteServidor");

        // Ensamblado interno (§6.1, paso 15): todas son clases de este paquete.
        notificador = new NotificadorObservadores();
        log.suscribir(notificador::evento);
        enrutador = new EnrutadorMensajes(conexiones, clienteServidor, notificador, json);
        sesiones = new ControlSesiones(usuarios, conexiones, log, enrutador, notificador);
        informes = new GeneradorInformes(usuarios, conexiones, mensajes, log);
        procesador = new ProcesadorMensajes(servicios, fabricaMensajes, mensajes, log, enrutador);
        listarUsuarios = new ManejadorListarUsuarios(usuarios, conexiones, clienteServidor);
        var manejadores = new FabricaManejadoresComando(sesiones, informes, gestorMensajes, usuarios, conexiones,
                mensajes, clienteServidor, protocolo::getIpServidor).crearTodos();
        var despachador = new DespachadorComandos(manejadores, conexiones);
        receptor = new ReceptorConexiones(clienteServidor, despachador, sesiones, conexiones, log, enrutador,
                protocolo::getIpServidor, cfg.puerto(), cfg.protocolo());
    }

    @Override
    public void iniciarServidor() {
        gestorMensajes.iniciar(cfg.hilosConsumidores(), procesador);
        try {
            protocolo.iniciar(cfg.puerto(), receptor);
        } catch (IOException e) {
            gestorMensajes.detener();
            throw new UncheckedIOException("No se pudo iniciar el transporte en el puerto " + cfg.puerto(), e);
        }
    }

    /** Apagado ordenado e idempotente; el pool de BD y Redis los cierra quien los creó (ServidorApp). */
    @Override
    public void detenerServidor() {
        if (!detenido.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Deteniendo el servidor");
        enrutador.difundir(enrutador.evento(TipoEventoPush.SESION_CERRADA, Map.of("motivo", "SERVIDOR_DETENIDO")));
        receptor.servidorDetenido();
        protocolo.detener();
        conexiones.resumen().forEach(s -> sesiones.cerrar(s.idSesion(), "SERVIDOR_DETENIDO"));
        gestorMensajes.detener();
    }

    @Override
    public void publicarEnForo(ContenidoDTO c) {
        encolarDelAdmin(Canal.FORO, null, c);
    }

    @Override
    public void enviarPrivado(String destino, ContenidoDTO c) {
        String admin = usuarios.administrador().codigo();
        if (destino == null || !usuarios.existe(destino) || destino.equals(admin)) {
            throw new ExcepcionNegocio(CodigoError.DESTINO_INEXISTENTE, "El destinatario no existe: " + destino);
        }
        encolarDelAdmin(Canal.PRIVADO, destino, c);
    }

    @Override
    public String informe(TipoInforme t, Map<String, String> filtros) {
        return json.aJson(GeneradorInformes.firmar(informes.generar(t, filtros), usuarios.administrador().codigo()));
    }

    @Override
    public List<SesionDTO> listarSesiones() {
        return conexiones.resumen();
    }

    @Override
    public List<EstadoUsuarioDTO> listarUsuarios() {
        return listarUsuarios.estados();
    }

    /** @throws ExcepcionNegocio NO_ENCONTRADO si la sesión ya no está activa. */
    @Override
    public void cerrarSesion(String idSesion) {
        ICanalCliente canal = conexiones.canalesActivos().stream()
                .filter(c -> c.getId().equals(idSesion))
                .findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_ENCONTRADO, "La sesión no está activa: " + idSesion));
        enrutador.enviarACanal(canal, enrutador.evento(TipoEventoPush.SESION_CERRADA, Map.of("motivo", "CERRADA_POR_ADMIN")));
        sesiones.cerrar(idSesion, "CERRADA_POR_ADMIN");
        canal.cerrar();
    }

    @Override
    public void suscribir(IObservadorServidor o) {
        notificador.suscribir(o);
    }

    @Override
    public String getIpServidor() {
        return protocolo.getIpServidor();
    }

    @Override
    public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
        if (idMensaje == null || etapa == null) {
            return Optional.empty();
        }
        return mensajes.imagen(idMensaje, etapa);
    }

    /** §6.4: la misma solicitud que arma un cliente, con el ADMIN como remitente y sin sesión. */
    private void encolarDelAdmin(Canal canal, String destino, ContenidoDTO c) {
        validar(c);
        var s = new SolicitudMensajeDTO(UUID.randomUUID().toString(), null, null,
                usuarios.administrador().codigo(), canal, destino, c, LocalDateTime.now());
        if (!gestorMensajes.encolar(s)) {
            throw new ExcepcionNegocio(CodigoError.COLA_LLENA, "La cola de mensajes está llena; intente de nuevo");
        }
    }

    /** Lo esencial de las reglas de ContenidoDTO (§7); el resto lo valida el procesamiento. */
    private void validar(ContenidoDTO c) {
        boolean valido = c != null && (c.tipo() == TipoMensaje.TEXTO
                ? c.texto() != null && !c.texto().isBlank() && c.texto().length() <= cfg.maxTextoCaracteres()
                : c.nombreArchivo() != null && !c.nombreArchivo().isBlank()
                        && c.datosBase64() != null && !c.datosBase64().isBlank());
        if (!valido) {
            throw new ExcepcionNegocio(CodigoError.CONTENIDO_INVALIDO, "El contenido del mensaje no es válido");
        }
    }
}
