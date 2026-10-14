package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Estructuras de datos de los mensajes (write-through): primero persiste con
 * AlmacenarInformacion y después indexa en memoria.
 */
public final class AlmacenMensajesMemoria implements IAlmacenMensajes {

    private final ConcurrentHashMap<String, MensajeDTO> porId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<MensajeDTO>> porConversacion = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<MensajeDTO> foro = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedQueue<MensajeDTO> enOrden = new ConcurrentLinkedQueue<>();
    private final IAlmacenarInformacion almacenamiento;

    public AlmacenMensajesMemoria(IAlmacenarInformacion almacenamiento) {
        this.almacenamiento = Objects.requireNonNull(almacenamiento, "almacenamiento");
    }

    @Override
    public MensajeDTO agregar(Mensaje m) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public Optional<MensajeDTO> buscar(String id) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public List<MensajeDTO> foro() {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public List<MensajeDTO> conversacion(String a, String b) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public List<MensajeDTO> todos() {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public Optional<byte[]> imagen(String id, TipoFiltro etapa) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public void cargarHistorial() {
        throw new UnsupportedOperationException("Fase 5");
    }
}
