package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base de los canales de cualquier transporte.
 *
 * <p>Una cola acotada y un único hilo escritor por canal: {@link #enviar} y
 * {@link #push} solo encolan, así un cliente lento no bloquea a quien le
 * escribe y nunca hay dos hilos escribiendo en el mismo socket. Si la cola se
 * llena, el canal se cierra.</p>
 *
 * <p>{@link #cerrar()} entrega lo ya encolado y después cierra el transporte;
 * lo que se encole después se descarta.</p>
 */
public abstract class CanalBase implements ICanalCliente {

    private static final Logger LOG = LoggerFactory.getLogger(CanalBase.class);
    private static final int CAPACIDAD_COLA = 256;
    /** Marca de fin en la cola (se compara por identidad). */
    private static final byte[] FIN = new byte[0];

    protected final String id;
    protected final String ipRemota;
    protected final ICodificadorMensajes codec;

    private final BlockingQueue<byte[]> salida = new ArrayBlockingQueue<>(CAPACIDAD_COLA);
    private final AtomicBoolean cerrando = new AtomicBoolean(false);
    private final AtomicBoolean iniciado = new AtomicBoolean(false);
    private volatile UsuarioDTO usuario;
    private volatile Thread escritor;

    protected CanalBase(String id, String ipRemota, ICodificadorMensajes codec) {
        this.id = Objects.requireNonNull(id, "id");
        this.ipRemota = Objects.requireNonNull(ipRemota, "ipRemota");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Arranca el hilo escritor. Se llama una vez, justo después de construir el
     * canal (nunca desde el constructor, para no publicar {@code this} a medias).
     */
    public final void iniciar() {
        if (iniciado.compareAndSet(false, true)) {
            escritor = Thread.ofVirtual().name("escritor-" + id).start(this::drenar);
        }
    }

    /** Escribe un mensaje ya codificado en el transporte. Solo lo llama el hilo escritor. */
    protected abstract void escribir(byte[] datos) throws IOException;

    /** Cierra el transporte. Debe ser idempotente. */
    protected abstract void cerrarTransporte();

    /**
     * @param b    bytes recibidos.
     * @param tipo clase del mensaje esperado.
     * @return el mensaje decodificado con el codec del canal.
     * @throws ProtocoloException si el mensaje es inválido.
     */
    protected <T> T decodificar(byte[] b, Class<T> tipo) throws ProtocoloException {
        return codec.decodificar(b, tipo);
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
    public Optional<UsuarioDTO> getUsuario() {
        return Optional.ofNullable(usuario);
    }

    @Override
    public void asociar(UsuarioDTO u) {
        this.usuario = Objects.requireNonNull(u, "u");
    }

    @Override
    public void enviar(RespuestaDTO r) {
        encolar(codec.codificar(r));
    }

    @Override
    public void push(EventoPushDTO e) {
        encolar(codec.codificar(e));
    }

    @Override
    public void cerrar() {
        if (cerrando.compareAndSet(false, true) && !salida.offer(FIN)) {
            cerrarYa();
        }
    }

    /** Cierra de inmediato, descartando lo pendiente. Idempotente. */
    public void cerrarYa() {
        cerrando.set(true);
        Thread hilo = escritor;
        if (hilo != null) {
            hilo.interrupt();
        }
        cerrarTransporte();
    }

    /** @return {@code true} si el canal ya no acepta mensajes. */
    public boolean estaCerrando() {
        return cerrando.get();
    }

    private void encolar(byte[] datos) {
        if (cerrando.get()) {
            LOG.debug("Canal {} cerrándose: se descarta un mensaje", id);
            return;
        }
        if (!salida.offer(datos)) {
            LOG.warn("Canal {} ({}) lento: cola de salida llena, se cierra", id, ipRemota);
            cerrarYa();
        }
    }

    private void drenar() {
        try {
            while (true) {
                byte[] datos = salida.take();
                if (datos == FIN) {
                    break;
                }
                escribir(datos);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            LOG.debug("Escritura fallida en el canal {}: {}", id, e.getMessage());
        } finally {
            cerrando.set(true);
            salida.clear();
            cerrarTransporte();
        }
    }
}
