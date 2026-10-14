package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servidor TCP: un hilo acepta conexiones y cada socket se atiende en su
 * propio hilo virtual ({@link ManejadorCliente}).
 */
public final class ServidorTCP implements IProtocoloComunicacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServidorTCP.class);
    private static final long ESPERA_CIERRE_MS = 2_000;

    private final int maxTramaBytes;
    private final int colaSalida;
    private final CodificadorTramas tramas;
    private final Set<CanalTCP> canales = ConcurrentHashMap.newKeySet();
    /** Serializa iniciar/detener (ReentrantLock: no fija hilos virtuales como synchronized). */
    private final ReentrantLock ciclo = new ReentrantLock();
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService hilos;
    private volatile IReceptorConexiones receptor;

    /**
     * @param maxTramaBytes tamaño máximo de una trama.
     * @param colaSalida    mensajes pendientes por canal.
     */
    public ServidorTCP(int maxTramaBytes, int colaSalida) {
        if (colaSalida <= 0) {
            throw new IllegalArgumentException("colaSalida debe ser mayor que 0: " + colaSalida);
        }
        this.maxTramaBytes = maxTramaBytes;
        this.colaSalida = colaSalida;
        this.tramas = new CodificadorTramas(maxTramaBytes);
    }

    @Override
    public void iniciar(int puerto, IReceptorConexiones r) throws IOException {
        Objects.requireNonNull(r, "r");
        ciclo.lock();
        try {
            if (estaActivo()) {
                throw new IllegalStateException("El servidor TCP ya está iniciado");
            }
            receptor = r;
            serverSocket = new ServerSocket(puerto);
            hilos = Executors.newVirtualThreadPerTaskExecutor();
            Thread.ofPlatform().name("tcp-aceptador").daemon().start(this::aceptarConexiones);
        } finally {
            ciclo.unlock();
        }
        LOG.info("Servidor TCP escuchando en {}:{} (trama máx. {} bytes)", getIpServidor(), getPuertoLocal(), maxTramaBytes);
    }

    @Override
    public void detener() {
        ciclo.lock();
        try {
            ServerSocket actual = serverSocket;
            if (actual == null || actual.isClosed()) {
                return;
            }
            try {
                actual.close();
            } catch (IOException e) {
                LOG.debug("Fallo cerrando el socket de escucha: {}", e.getMessage());
            }
            canales.forEach(CanalTCP::cerrar);
            hilos.shutdown();
            try {
                if (!hilos.awaitTermination(ESPERA_CIERRE_MS, TimeUnit.MILLISECONDS)) {
                    canales.forEach(CanalTCP::cerrarYa);
                    hilos.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hilos.shutdownNow();
            }
        } finally {
            ciclo.unlock();
        }
        LOG.info("Servidor TCP detenido");
    }

    @Override
    public boolean estaActivo() {
        ServerSocket actual = serverSocket;
        return actual != null && !actual.isClosed();
    }

    @Override
    public String getIpServidor() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }

    /** @return el puerto real de escucha (útil cuando se inicia con el puerto 0). */
    public int getPuertoLocal() {
        ServerSocket actual = serverSocket;
        if (actual == null) {
            throw new IllegalStateException("El servidor TCP no está iniciado");
        }
        return actual.getLocalPort();
    }

    /** Quita un canal terminado del registro interno. */
    void olvidar(CanalTCP canal) {
        canales.remove(canal);
    }

    private void aceptarConexiones() {
        ServerSocket escucha = serverSocket;
        while (!escucha.isClosed()) {
            Socket socket;
            try {
                socket = escucha.accept();
            } catch (IOException e) {
                if (!escucha.isClosed()) {
                    LOG.warn("Fallo aceptando una conexión: {}", e.getMessage());
                }
                continue;
            }
            atenderSocket(socket);
        }
    }

    private void atenderSocket(Socket socket) {
        CanalTCP canal;
        try {
            canal = new CanalTCP(socket, tramas, colaSalida);
        } catch (IOException e) {
            LOG.warn("No se pudo preparar la conexión desde {}: {}", socket.getInetAddress(), e.getMessage());
            cerrarSilencioso(socket);
            return;
        }
        canal.iniciar();
        canales.add(canal);
        try {
            hilos.submit(new ManejadorCliente(canal, receptor, this));
        } catch (RejectedExecutionException e) {
            // El servidor se está deteniendo.
            canales.remove(canal);
            canal.cerrarYa();
        }
    }

    private static void cerrarSilencioso(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Fallo cerrando un socket rechazado: {}", e.getMessage());
        }
    }
}
