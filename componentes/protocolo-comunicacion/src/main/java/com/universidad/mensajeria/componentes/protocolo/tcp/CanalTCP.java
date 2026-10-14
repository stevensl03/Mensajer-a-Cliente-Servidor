package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.fachada.ICanalCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Canal sobre un socket TCP.
 *
 * <p>Una cola de salida acotada y un único hilo escritor: {@link #enviar}
 * solo encola, así un cliente lento no bloquea a quien le escribe y nunca hay
 * dos hilos escribiendo en el mismo socket. Si la cola se llena, el canal se
 * cierra. Lee únicamente el hilo de {@link ManejadorCliente}.</p>
 *
 * <p>{@link #cerrar()} entrega lo ya encolado y después cierra el socket; lo
 * que se encole después se descarta.</p>
 */
public final class CanalTCP implements ICanalCliente {

    private static final Logger LOG = LoggerFactory.getLogger(CanalTCP.class);
    /** Marca de fin en la cola (se compara por identidad). */
    private static final String FIN = new String("FIN");

    private final String id = UUID.randomUUID().toString();
    private final Socket socket;
    private final String ipRemota;
    private final CodificadorTramas tramas;
    private final DataInputStream entrada;
    private final DataOutputStream salidaSocket;
    private final BlockingQueue<String> salida;
    private final AtomicBoolean cerrando = new AtomicBoolean(false);
    private final AtomicBoolean iniciado = new AtomicBoolean(false);
    private volatile Thread escritor;

    /**
     * @param socket     socket aceptado.
     * @param tramas     delimitador de tramas.
     * @param colaSalida capacidad de la cola de salida.
     * @throws IOException si no se pueden obtener los flujos del socket.
     */
    public CanalTCP(Socket socket, CodificadorTramas tramas, int colaSalida) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.tramas = Objects.requireNonNull(tramas, "tramas");
        this.salida = new ArrayBlockingQueue<>(colaSalida);
        this.ipRemota = socket.getInetAddress().getHostAddress();
        // Sin comando de latido en el protocolo: el sistema operativo detecta pares caídos.
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        this.entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.salidaSocket = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /**
     * Arranca el hilo escritor. Se llama una vez, justo después de construir el
     * canal (nunca desde el constructor, para no publicar {@code this} a medias).
     */
    void iniciar() {
        if (iniciado.compareAndSet(false, true)) {
            escritor = Thread.ofVirtual().name("escritor-" + id).start(this::drenar);
        }
    }

    /** Lee la siguiente trama. Solo la usa el hilo lector. */
    byte[] leerTrama() throws IOException {
        return tramas.leer(entrada);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getIpRemota() {
        return ipRemota;
    }

    @Override
    public void enviar(String json) {
        Objects.requireNonNull(json, "json");
        if (cerrando.get()) {
            LOG.debug("Canal {} cerrándose: se descarta un mensaje", id);
            return;
        }
        if (!salida.offer(json)) {
            LOG.warn("Canal {} ({}) lento: cola de salida llena, se cierra", id, ipRemota);
            cerrarYa();
        }
    }

    @Override
    public void cerrar() {
        if (cerrando.compareAndSet(false, true) && (!iniciado.get() || !salida.offer(FIN))) {
            cerrarYa();
        }
    }

    @Override
    public boolean estaAbierto() {
        return !cerrando.get() && !socket.isClosed();
    }

    /** Cierra de inmediato, descartando lo pendiente. Idempotente. */
    void cerrarYa() {
        cerrando.set(true);
        Thread hilo = escritor;
        if (hilo != null) {
            hilo.interrupt();
        }
        cerrarSocket();
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
        } catch (IOException | RuntimeException e) {
            LOG.debug("Escritura fallida en el canal {}: {}", id, e.getMessage());
        } finally {
            cerrando.set(true);
            salida.clear();
            cerrarSocket();
        }
    }

    private void cerrarSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Fallo cerrando el socket del canal {}: {}", id, e.getMessage());
        }
    }
}
