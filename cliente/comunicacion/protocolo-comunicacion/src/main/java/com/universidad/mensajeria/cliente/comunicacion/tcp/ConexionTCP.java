package com.universidad.mensajeria.cliente.comunicacion.tcp;

import com.universidad.mensajeria.cliente.comunicacion.IConexionServidor;
import com.universidad.mensajeria.cliente.comunicacion.IReceptorServidor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Conexión TCP con el servidor.
 *
 * <p>Por cada conexión hay un hilo lector, que entrega las tramas al receptor, y
 * un único hilo escritor con una cola de salida acotada: {@link #enviar} solo
 * encola, así nunca hay dos hilos escribiendo en el socket.</p>
 *
 * <p>Es reconectable (D-06): el estado de cada conexión vive en un
 * {@link Enlace} propio, de modo que el hilo lector de una conexión ya cerrada
 * no puede afectar a la siguiente. {@code alDesconectar} se llama una sola vez
 * por conexión, desde su hilo lector.</p>
 */
public final class ConexionTCP implements IConexionServidor {

    private static final Logger LOG = LoggerFactory.getLogger(ConexionTCP.class);

    /** Mensajes pendientes de envío antes de rechazar nuevos (igual que {@code sesion.colaSalida} del servidor). */
    static final int CAPACIDAD_SALIDA = 256;
    /** Espera máxima para establecer la conexión. */
    static final Duration PLAZO_CONEXION = Duration.ofSeconds(5);
    /** Espera máxima para entregar lo encolado al cerrar. */
    static final Duration PLAZO_CIERRE = Duration.ofSeconds(2);

    static final String MOTIVO_CLIENTE = "Conexión cerrada por el cliente";
    static final String MOTIVO_SERVIDOR = "El servidor cerró la conexión";

    /** Marca de fin en la cola (se compara por identidad). */
    private static final String FIN = new String("FIN");

    private final CodificadorTramas tramas;
    private final ReentrantLock cambio = new ReentrantLock();
    private volatile Enlace actual;
    private volatile String ipServidor;

    /**
     * @param maxTramaBytes tamaño máximo de una trama.
     */
    public ConexionTCP(int maxTramaBytes) {
        this.tramas = new CodificadorTramas(maxTramaBytes);
    }

    @Override
    public void conectar(String host, int puerto, IReceptorServidor r) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(r, "r");
        cambio.lock();
        try {
            if (estaConectada()) {
                throw new IllegalStateException("Ya hay una conexión abierta con " + ipServidor);
            }
            Socket socket = new Socket();
            try {
                // Sin comando de latido en el protocolo: el sistema operativo detecta pares caídos.
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(host, puerto), (int) PLAZO_CONEXION.toMillis());
                var enlace = new Enlace(socket, r);
                ipServidor = socket.getInetAddress().getHostAddress();
                actual = enlace;
                enlace.iniciar();
            } catch (IOException | RuntimeException e) {
                cerrarSilencioso(socket);
                if (e instanceof IOException io) {
                    throw new UncheckedIOException("No fue posible conectar con " + host + ":" + puerto, io);
                }
                throw (RuntimeException) e;
            }
        } finally {
            cambio.unlock();
        }
    }

    @Override
    public void enviar(String json) {
        Objects.requireNonNull(json, "json");
        Enlace enlace = actual;
        if (enlace == null || enlace.cerrando.get()) {
            throw new IllegalStateException("No hay conexión con el servidor");
        }
        tramas.validar(json.getBytes(StandardCharsets.UTF_8).length);
        if (!enlace.salida.offer(json)) {
            throw new IllegalStateException("La cola de salida está llena (" + CAPACIDAD_SALIDA + " mensajes)");
        }
    }

    @Override
    public void cerrar() {
        Enlace enlace = actual;
        if (enlace != null) {
            enlace.cerrar();
        }
    }

    @Override
    public String getIpServidor() {
        return ipServidor;
    }

    @Override
    public boolean estaConectada() {
        Enlace enlace = actual;
        return enlace != null && !enlace.cerrando.get() && !enlace.socket.isClosed();
    }

    private static void cerrarSilencioso(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Fallo cerrando el socket: {}", e.getMessage());
        }
    }

    /** Estado de una conexión: socket, flujos, cola de salida y sus dos hilos. */
    private final class Enlace {

        private final Socket socket;
        private final IReceptorServidor receptor;
        private final DataInputStream entrada;
        private final DataOutputStream salidaSocket;
        private final BlockingQueue<String> salida = new ArrayBlockingQueue<>(CAPACIDAD_SALIDA);
        private final AtomicBoolean cerrando = new AtomicBoolean(false);
        /** Solo {@link #cerrar()} la marca: distingue el cierre pedido de una caída. */
        private final AtomicBoolean porCliente = new AtomicBoolean(false);
        private final AtomicBoolean avisado = new AtomicBoolean(false);
        private volatile Thread lector;
        private volatile Thread escritor;

        Enlace(Socket socket, IReceptorServidor receptor) throws IOException {
            this.socket = socket;
            this.receptor = receptor;
            this.entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.salidaSocket = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        /** Arranca los hilos fuera del constructor, para no publicar el enlace a medias. */
        void iniciar() {
            String nombre = socket.getRemoteSocketAddress().toString();
            escritor = Thread.ofVirtual().name("escritor-" + nombre).start(this::drenar);
            lector = Thread.ofVirtual().name("lector-" + nombre).start(this::leer);
        }

        /** Entrega lo encolado (con plazo) y cierra. Idempotente. */
        void cerrar() {
            porCliente.set(true);
            if (!cerrando.compareAndSet(false, true)) {
                return;
            }
            Thread hilo = escritor;
            if (salida.offer(FIN) && hilo != null && Thread.currentThread() != hilo) {
                try {
                    if (!hilo.join(PLAZO_CIERRE)) {
                        LOG.warn("No se entregó lo pendiente en {} s; se cierra igual", PLAZO_CIERRE.toSeconds());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            cerrarYa();
        }

        /** Cierra de inmediato, descartando lo pendiente. Idempotente. */
        void cerrarYa() {
            cerrando.set(true);
            Thread hilo = escritor;
            if (hilo != null && Thread.currentThread() != hilo) {
                hilo.interrupt();
            }
            cerrarSilencioso(socket);
        }

        private void drenar() {
            try {
                while (true) {
                    String json = salida.take();
                    if (json == FIN) {
                        break;
                    }
                    tramas.escribir(salidaSocket, json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LOG.debug("Escritura fallida hacia el servidor: {}", e.getMessage());
            } catch (RuntimeException e) {
                LOG.error("Error inesperado escribiendo hacia el servidor", e);
            } finally {
                salida.clear();
                // El lector detecta el socket cerrado y avisa la desconexión.
                cerrarYa();
            }
        }

        private void leer() {
            String motivo = MOTIVO_SERVIDOR;
            try {
                while (true) {
                    String json = new String(tramas.leer(entrada), StandardCharsets.UTF_8);
                    try {
                        receptor.alRecibir(json);
                    } catch (RuntimeException e) {
                        LOG.error("Error atendiendo una trama del servidor", e);
                    }
                }
            } catch (EOFException e) {
                motivo = porCliente.get() ? MOTIVO_CLIENTE : MOTIVO_SERVIDOR;
            } catch (StreamCorruptedException e) {
                LOG.warn("Trama inválida del servidor: {}. Se cierra", e.getMessage());
                motivo = "Trama inválida del servidor: " + e.getMessage();
            } catch (IOException e) {
                motivo = porCliente.get() ? MOTIVO_CLIENTE : "Error de red: " + e.getMessage();
            } catch (RuntimeException e) {
                LOG.error("Error inesperado leyendo del servidor", e);
                motivo = "Error inesperado: " + e.getMessage();
            } finally {
                cerrarYa();
                avisar(motivo);
            }
        }

        private void avisar(String motivo) {
            if (avisado.compareAndSet(false, true)) {
                try {
                    receptor.alDesconectar(motivo);
                } catch (RuntimeException e) {
                    LOG.error("Error al avisar la desconexión", e);
                }
            }
        }
    }
}
