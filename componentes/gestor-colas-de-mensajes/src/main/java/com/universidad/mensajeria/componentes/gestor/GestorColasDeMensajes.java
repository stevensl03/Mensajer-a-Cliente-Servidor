package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.soporte.mensajes.Mensaje;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementación de las estructuras en memoria (componente
 * "GestorColasDeMensajes").
 *
 * <p>Usa las cinco estructuras elegidas (C-09), todas thread-safe:</p>
 * <ul>
 *   <li>{@link ConcurrentMap} {@code porId} para consulta O(1) por UUID.</li>
 *   <li>{@link ConcurrentMap} {@code porUsuario} (código → ids) para privados.</li>
 *   <li>{@link ConcurrentLinkedQueue} de difusión para el foro en orden FIFO.</li>
 *   <li>{@link ConcurrentLinkedDeque} de pendientes para encolar/desencolar.</li>
 * </ul>
 */
public class GestorColasDeMensajes implements InterfazGestorMensajes {

    private final ConcurrentMap<String, Mensaje> porId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> porUsuario = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Mensaje> difusion = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedDeque<Mensaje> pendientes = new ConcurrentLinkedDeque<>();

    @Override
    public void encolar(Mensaje mensaje) {
        // TODO: porId.put, índices por usuario, difusión y cola de pendientes.
    }

    @Override
    public Optional<Mensaje> siguientePendiente() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public Optional<Mensaje> buscarPorId(String id) {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<Mensaje> listarPorUsuario(String codigoUsuario) {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<Mensaje> listarTodos() {
        // TODO
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public List<Mensaje> listarForo() {
        // TODO: copia de la cola de difusión en orden cronológico.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public void recargarDesdeHistorial(List<Mensaje> mensajes) {
        // TODO (fase 2): estas estructuras pasan a AlmacenMensajesMemoria según el diagrama.
    }
}
