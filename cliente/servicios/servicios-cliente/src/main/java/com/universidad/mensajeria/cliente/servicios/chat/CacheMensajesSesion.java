package com.universidad.mensajeria.cliente.servicios.chat;

import com.universidad.mensajeria.cliente.persistencia.IRepositorioMensajesLocal;
import com.universidad.mensajeria.dto.MensajeDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caché L1 en memoria de las conversaciones de la sesión: LRU de, como mucho,
 * {@code max} conversaciones ({@code cache.mensajes.max}).
 *
 * <p>Solo guarda conversaciones completas: {@link #agregar} añade a una
 * conversación ya cargada con {@link #cargar}, para que un mensaje suelto no
 * oculte el historial que está en H2 (D-18).</p>
 */
public final class CacheMensajesSesion {

    static final Comparator<MensajeDTO> CRONOLOGICO =
            Comparator.comparing(MensajeDTO::fecha).thenComparing(MensajeDTO::id);

    private final int max;
    private final ReentrantLock candado = new ReentrantLock();
    private final LinkedHashMap<String, List<MensajeDTO>> mapa;

    /**
     * @param max conversaciones que se conservan.
     */
    public CacheMensajesSesion(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max debe ser mayor que 0: " + max);
        }
        this.max = max;
        this.mapa = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<MensajeDTO>> mayor) {
                return size() > CacheMensajesSesion.this.max;
            }
        };
    }

    /**
     * @param conversacion clave de la conversación.
     * @return una copia ordenada, o una lista vacía si no está cargada.
     */
    public List<MensajeDTO> get(String conversacion) {
        candado.lock();
        try {
            List<MensajeDTO> l = mapa.get(conversacion);
            return l == null ? List.of() : List.copyOf(l);
        } finally {
            candado.unlock();
        }
    }

    /** @return si la conversación está cargada. */
    public boolean contiene(String conversacion) {
        candado.lock();
        try {
            return mapa.containsKey(conversacion);
        } finally {
            candado.unlock();
        }
    }

    /**
     * Carga una conversación completa (reemplaza la anterior).
     *
     * @param conversacion clave de la conversación.
     * @param mensajes     todos sus mensajes conocidos.
     */
    public void cargar(String conversacion, List<MensajeDTO> mensajes) {
        Objects.requireNonNull(conversacion, "conversacion");
        var lista = new ArrayList<MensajeDTO>();
        for (MensajeDTO m : mensajes) {
            if (lista.stream().noneMatch(x -> x.id().equals(m.id()))) {
                lista.add(m);
            }
        }
        lista.sort(CRONOLOGICO);
        candado.lock();
        try {
            mapa.put(conversacion, lista);
        } finally {
            candado.unlock();
        }
    }

    /**
     * Añade el mensaje a su conversación si está cargada y aún no lo tiene.
     *
     * @param m mensaje.
     */
    public void agregar(MensajeDTO m) {
        String clave = IRepositorioMensajesLocal.claveConversacion(m);
        candado.lock();
        try {
            List<MensajeDTO> l = mapa.get(clave);
            if (l != null && l.stream().noneMatch(x -> x.id().equals(m.id()))) {
                l.add(m);
                l.sort(CRONOLOGICO);
            }
        } finally {
            candado.unlock();
        }
    }

    /** Vacía la caché (fin de sesión). */
    public void limpiar() {
        candado.lock();
        try {
            mapa.clear();
        } finally {
            candado.unlock();
        }
    }
}
