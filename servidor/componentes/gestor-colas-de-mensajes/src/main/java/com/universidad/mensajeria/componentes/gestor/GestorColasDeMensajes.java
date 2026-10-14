package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase principal del componente GestorColasDeMensajes: cola productor /
 * consumidor de mensajes entrantes. El hilo de red solo encola; N hilos
 * consumidores procesan. No conoce Mensajes ni la red.
 *
 * <p>Los consumidores son hilos de plataforma porque procesar imágenes
 * consume CPU. Un error al procesar una solicitud se registra y el
 * consumidor sigue con la siguiente.</p>
 *
 * <p>Ciclo de vida: acepta solicitudes desde que se crea hasta que se llama a
 * {@link #detener()}; las que llegan antes de {@link #iniciar} esperan en la
 * cola.</p>
 */
public final class GestorColasDeMensajes implements IGestorMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(GestorColasDeMensajes.class);
    private static final Duration PLAZO_DETENER = Duration.ofSeconds(5);
    private static final long ESPERA_POLL_MS = 100;

    private final BlockingQueue<SolicitudMensajeDTO> cola;
    private final Duration plazoDetener;
    /** {@code true} mientras se aceptan solicitudes nuevas. */
    private final AtomicBoolean activo = new AtomicBoolean(true);
    private final AtomicBoolean iniciado = new AtomicBoolean(false);
    private volatile ExecutorService consumidores;
    private volatile IConsumidorMensajes consumidor;

    /**
     * @param capacidad capacidad de la cola ({@code cola.capacidad}).
     */
    public GestorColasDeMensajes(int capacidad) {
        this(capacidad, PLAZO_DETENER);
    }

    /** Permite a las pruebas acortar el plazo de {@link #detener()}. */
    GestorColasDeMensajes(int capacidad, Duration plazoDetener) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("capacidad debe ser mayor que 0: " + capacidad);
        }
        this.cola = new LinkedBlockingQueue<>(capacidad);
        this.plazoDetener = Objects.requireNonNull(plazoDetener, "plazoDetener");
    }

    @Override
    public void iniciar(int hilos, IConsumidorMensajes c) {
        if (hilos <= 0) {
            throw new IllegalArgumentException("hilos debe ser mayor que 0: " + hilos);
        }
        Objects.requireNonNull(c, "c");
        if (!activo.get()) {
            throw new IllegalStateException("La cola ya se detuvo");
        }
        if (!iniciado.compareAndSet(false, true)) {
            throw new IllegalStateException("La cola ya está iniciada");
        }
        consumidor = c;
        var numero = new AtomicInteger();
        consumidores = Executors.newFixedThreadPool(hilos,
                r -> Thread.ofPlatform().name("consumidor-" + numero.incrementAndGet()).unstarted(r));
        for (int i = 0; i < hilos; i++) {
            consumidores.submit(this::consumir);
        }
        LOG.info("Cola de mensajes iniciada: {} consumidores, capacidad {}", hilos,
                cola.remainingCapacity() + cola.size());
    }

    /** {@inheritDoc} Usa {@code offer}: nunca bloquea al hilo de red. */
    @Override
    public boolean encolar(SolicitudMensajeDTO s) {
        Objects.requireNonNull(s, "s");
        if (!activo.get()) {
            return false;
        }
        boolean aceptada = cola.offer(s);
        if (!aceptada) {
            LOG.warn("Cola de mensajes llena: se rechaza el mensaje {}", s.idMensaje());
        }
        return aceptada;
    }

    @Override
    public int pendientes() {
        return cola.size();
    }

    /**
     * Deja de aceptar solicitudes y espera a que los consumidores vacíen la
     * cola, como máximo el plazo (5 s). Lo que quede después se descarta y se
     * registra. Idempotente.
     */
    @Override
    public void detener() {
        if (!activo.compareAndSet(true, false)) {
            return;
        }
        ExecutorService hilos = consumidores;
        if (hilos == null) {
            descartarPendientes();
            return;
        }
        hilos.shutdown();
        try {
            if (!hilos.awaitTermination(plazoDetener.toMillis(), TimeUnit.MILLISECONDS)) {
                hilos.shutdownNow();
                descartarPendientes();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            hilos.shutdownNow();
            descartarPendientes();
        }
        LOG.info("Cola de mensajes detenida");
    }

    /** Bucle de cada consumidor: sigue mientras esté activa o queden solicitudes. */
    private void consumir() {
        while (activo.get() || !cola.isEmpty()) {
            SolicitudMensajeDTO s;
            try {
                s = cola.poll(ESPERA_POLL_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (s == null) {
                continue;
            }
            try {
                consumidor.procesar(s);
            } catch (RuntimeException e) {
                LOG.error("Error procesando el mensaje {}", s.idMensaje(), e);
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
        }
    }

    private void descartarPendientes() {
        int descartadas = cola.size();
        cola.clear();
        if (descartadas > 0) {
            LOG.warn("Se venció el plazo al detener: {} mensajes sin procesar se descartan", descartadas);
        }
    }
}
