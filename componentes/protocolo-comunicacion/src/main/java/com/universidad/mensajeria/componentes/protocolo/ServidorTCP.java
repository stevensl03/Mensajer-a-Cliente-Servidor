package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servidor TCP: un hilo virtual acepta conexiones y cada socket se atiende en
 * su propio hilo virtual ({@link ManejadorCliente}).
 */
public final class ServidorTCP extends ServidorBase {

    private static final Logger LOG = LoggerFactory.getLogger(ServidorTCP.class);
    private static final long ESPERA_CIERRE_MS = 2_000;

    private final CodificadorTramas tramas;
    private final Set<CanalTCP> canales = ConcurrentHashMap.newKeySet();
    /** Serializa iniciar/detener (ReentrantLock: no fija hilos virtuales como synchronized). */
    private final ReentrantLock ciclo = new ReentrantLock();
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService pool;

    public ServidorTCP(IClienteServidor clienteServidor, IGestorConexiones conexiones,
                       ICodificadorMensajes codec, CodificadorTramas tramas) {
        super(clienteServidor, conexiones, codec);
        this.tramas = Objects.requireNonNull(tramas, "tramas");
    }

    @Override
    public void iniciar(int puerto) throws IOException {
        ciclo.lock();
        try {
            if (estaActivo()) {
                throw new IllegalStateException("El servidor TCP ya está iniciado");
            }
            serverSocket = new ServerSocket(puerto);
            pool = Executors.newVirtualThreadPerTaskExecutor();
            Thread.ofVirtual().name("tcp-aceptador").start(this::aceptarConexiones);
        } finally {
            ciclo.unlock();
        }
        LOG.info("Servidor TCP escuchando en {}:{}", getIpServidor(), getPuertoLocal());
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
            } catch (IOException ignorada) {
                // Ya no acepta conexiones.
            }
            canales.forEach(CanalBase::cerrar);
            pool.shutdown();
            try {
                if (!pool.awaitTermination(ESPERA_CIERRE_MS, TimeUnit.MILLISECONDS)) {
                    canales.forEach(CanalBase::cerrarYa);
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
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
            canal = new CanalTCP(socket, codec, tramas);
        } catch (IOException e) {
            LOG.warn("No se pudo preparar la conexión desde {}: {}", socket.getInetAddress(), e.getMessage());
            cerrarSilencioso(socket);
            return;
        }
        canal.iniciar();
        canales.add(canal);
        try {
            pool.submit(new ManejadorCliente(canal, this));
        } catch (RejectedExecutionException e) {
            // El servidor se está deteniendo.
            canales.remove(canal);
            canal.cerrarYa();
        }
    }

    private static void cerrarSilencioso(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignorada) {
            // Nada más que hacer.
        }
    }
}
