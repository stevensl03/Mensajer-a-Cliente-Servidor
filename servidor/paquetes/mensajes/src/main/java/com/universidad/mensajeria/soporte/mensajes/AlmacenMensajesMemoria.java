package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private static final Comparator<MensajeDTO> POR_FECHA = Comparator.comparing(MensajeDTO::fecha);

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
        List<ImagenProcesadaDTO> versiones = m instanceof MensajeImagen imagen ? imagen.versiones() : List.of();
        MensajeDTO guardado = almacenamiento.guardarMensaje(m.aDTO(), versiones);
        indexar(guardado);
        return guardado;
    }

    @Override
    public Optional<MensajeDTO> buscar(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<MensajeDTO> foro() {
        return foro.stream().sorted(POR_FECHA).toList();
    }

    @Override
    public List<MensajeDTO> conversacion(String a, String b) {
        List<MensajeDTO> lista = porConversacion.get(Mensaje.claveConversacion(a, b));
        if (lista == null) {
            return List.of();
        }
        synchronized (lista) {
            return lista.stream().sorted(POR_FECHA).toList();
        }
    }

    @Override
    public List<MensajeDTO> todos() {
        return List.copyOf(enOrden);
    }

    @Override
    public Optional<byte[]> imagen(String id, TipoFiltro etapa) {
        return almacenamiento.leerImagen(id, etapa);
    }

    @Override
    public void cargarHistorial() {
        almacenamiento.historialMensajes().stream().sorted(POR_FECHA).forEach(this::indexar);
    }

    private void indexar(MensajeDTO m) {
        if (porId.putIfAbsent(m.id(), m) != null) {
            return;
        }
        enOrden.add(m);
        if (m.canal() == Canal.FORO) {
            foro.addLast(m);
        } else {
            porConversacion.computeIfAbsent(Mensaje.claveConversacion(m.remitente(), m.destinatario()),
                    k -> Collections.synchronizedList(new ArrayList<>())).add(m);
        }
    }
}
