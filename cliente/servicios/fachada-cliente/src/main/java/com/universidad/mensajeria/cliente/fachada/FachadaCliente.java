package com.universidad.mensajeria.cliente.fachada;

import com.fasterxml.jackson.core.type.TypeReference;
import com.universidad.mensajeria.cliente.protocolo.IOyentePush;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.servicios.chat.IServicioChat;
import com.universidad.mensajeria.cliente.servicios.informes.IServicioInformes;
import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.cliente.servicios.usuarios.IServicioUsuarios;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Fachada del cliente: orquesta los servicios y reparte los eventos push a
 * los observadores (cliente §5).
 *
 * <p>La sesión termina una sola vez (R-49): por {@code logout()} (sin aviso),
 * por {@code SESION_CERRADA} o por la caída de la conexión (con un único
 * {@code onDesconexion}). Los push de usuarios que llegan antes de terminar el
 * login se ignoran (R-30).</p>
 */
public final class FachadaCliente implements IFachadaCliente, IOyentePush {

    private static final TypeReference<MensajeDTO> MENSAJE = new TypeReference<>() { };

    private final IServicioUsuarios usuarios;
    private final IServicioChat chat;
    private final IServicioInformes informes;
    private final IProtocoloCliente protocolo;
    private final ISerializador json;
    private final CopyOnWriteArrayList<IObservadorCliente> observadores = new CopyOnWriteArrayList<>();
    private final ReentrantLock conexion = new ReentrantLock();
    private final AtomicBoolean sesionActiva = new AtomicBoolean(false);
    private volatile boolean conectada;
    private volatile String host;
    private volatile int puerto;
    private volatile String ipServidor;

    public FachadaCliente(IServicioUsuarios usuarios, IServicioChat chat, IServicioInformes informes,
                          IProtocoloCliente protocolo, ISerializador json) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.chat = Objects.requireNonNull(chat, "chat");
        this.informes = Objects.requireNonNull(informes, "informes");
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public String conectar(String host, int puerto) {
        Objects.requireNonNull(host, "host");
        conexion.lock();
        try {
            if (conectada && host.equals(this.host) && puerto == this.puerto) {
                return ipServidor;
            }
            if (conectada) {
                protocolo.desconectar();
            }
            String ip = protocolo.conectar(host, puerto);
            this.host = host;
            this.puerto = puerto;
            this.ipServidor = ip;
            this.conectada = true;
            return ip;
        } finally {
            conexion.unlock();
        }
    }

    @Override
    public CompletableFuture<UsuarioDTO> login(String codigo, String clave) {
        if (!conectada) {
            return CompletableFuture.failedFuture(new IllegalStateException("Primero hay que conectarse al servidor"));
        }
        return usuarios.login(codigo, clave, puerto).thenApply(u -> {
            chat.iniciarSesion(u, usuarios.sesionLocal());
            sesionActiva.set(true);
            return u;
        });
    }

    @Override
    public void logout() {
        if (sesionActiva.compareAndSet(true, false)) {
            cerrarLocal();
        } else if (conectada) {
            // Sin sesión (p. ej. tras credenciales inválidas): solo queda la conexión abierta.
            conectada = false;
            protocolo.desconectar();
        }
    }

    @Override
    public CompletableFuture<List<EstadoUsuarioDTO>> usuarios() {
        return usuarios.listar();
    }

    @Override
    public CompletableFuture<Void> enviarForo(ContenidoDTO c) {
        return chat.enviar(Canal.FORO, null, c);
    }

    @Override
    public CompletableFuture<Void> enviarPrivado(String destino, ContenidoDTO c) {
        return chat.enviar(Canal.PRIVADO, destino, c);
    }

    @Override
    public List<MensajeDTO> historial(String conversacion) {
        List<MensajeDTO> locales = chat.historial(conversacion);
        chat.sincronizar(conversacion).thenAccept(nuevos -> {
            if (sesionActiva.get()) {
                nuevos.forEach(m -> notificar(o -> o.onMensaje(m)));
            }
        });
        return locales;
    }

    @Override
    public CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
        return chat.imagen(idMensaje, etapa);
    }

    @Override
    public CompletableFuture<InformeRecibido> informe(TipoInforme t, Map<String, String> filtros) {
        return informes.obtener(t, filtros);
    }

    @Override
    public String getIpServidor() {
        return ipServidor;
    }

    @Override
    public int getPuerto() {
        return puerto;
    }

    @Override
    public void suscribir(IObservadorCliente o) {
        observadores.addIfAbsent(Objects.requireNonNull(o, "o"));
    }

    @Override
    public Optional<UsuarioDTO> usuarioActual() {
        return sesionActiva.get() ? usuarios.actual() : Optional.empty();
    }

    @Override
    public long sesionLocal() {
        return sesionActiva.get() ? usuarios.sesionLocal() : -1;
    }

    @Override
    public long mensajesGuardados() {
        return chat.mensajesGuardados();
    }

    /** Llega en el hilo lector de la red; lo que bloquea se hace en otro hilo. */
    @Override
    public void onPush(EventoPushDTO e) {
        switch (e.evento()) {
            case MENSAJE_NUEVO -> {
                MensajeDTO m = json.convertir(e.datos().get("mensaje"), MENSAJE);
                chat.recibir(m);
                if (sesionActiva.get()) {
                    notificar(o -> o.onMensaje(m));
                }
            }
            case USUARIO_CONECTADO, USUARIO_DESCONECTADO -> {
                if (sesionActiva.get()) {
                    // Si falla, la lista anterior sigue en pantalla hasta el próximo cambio.
                    usuarios.listar().thenAccept(l -> notificar(o -> o.onUsuarios(l)));
                }
            }
            case SESION_CERRADA -> terminarSesion(e.datos() == null ? "SESION_CERRADA"
                    : e.datos().path("motivo").asText("SESION_CERRADA"));
            case INFO_SERVIDOR, CUPO_LLENO -> {
                // INFO_SERVIDOR lo atiende el protocolo; CUPO_LLENO está reservado (servidor §7).
            }
        }
    }

    @Override
    public void onDesconexion(String motivo) {
        conectada = false;
        terminarSesion(motivo);
    }

    /** Cierra lo local y avisa una sola vez, fuera del hilo lector (el LOGOUT no debe esperar a la red). */
    private void terminarSesion(String motivo) {
        if (sesionActiva.compareAndSet(true, false)) {
            CompletableFuture.runAsync(() -> {
                try {
                    cerrarLocal();
                } finally {
                    notificar(o -> o.onDesconexion(motivo));
                }
            });
        }
    }

    private void cerrarLocal() {
        try {
            usuarios.logout();
        } finally {
            chat.cerrarSesion();
            conectada = false;
            protocolo.desconectar();
        }
    }

    /** Un observador que falla no impide avisar a los demás; el primer error se relanza al final. */
    private void notificar(Consumer<IObservadorCliente> aviso) {
        RuntimeException error = null;
        for (IObservadorCliente o : observadores) {
            try {
                aviso.accept(o);
            } catch (RuntimeException e) {
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            }
        }
        if (error != null) {
            throw error;
        }
    }
}
