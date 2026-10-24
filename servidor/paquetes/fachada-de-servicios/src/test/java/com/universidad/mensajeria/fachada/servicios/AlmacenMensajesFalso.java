package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;
import com.universidad.mensajeria.soporte.mensajes.Mensaje;
import com.universidad.mensajeria.soporte.mensajes.MensajeImagen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** IAlmacenMensajes en memoria: asigna rutas falsas a las imágenes y guarda sus bytes. */
final class AlmacenMensajesFalso implements IAlmacenMensajes {

    final List<MensajeDTO> mensajes = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> imagenes = new ConcurrentHashMap<>();
    volatile boolean fallar;

    @Override
    public MensajeDTO agregar(Mensaje m) {
        if (fallar) {
            throw new IllegalStateException("BD caída");
        }
        MensajeDTO base = m.aDTO();
        MensajeDTO dto = base;
        if (m instanceof MensajeImagen imagen) {
            List<ImagenVersionDTO> versiones = imagen.versiones().stream()
                    .map(v -> {
                        imagenes.put(base.id() + ":" + v.etapa(), v.datos());
                        String nombre = v.etapa() == TipoFiltro.ORIGINAL
                                ? base.nombreArchivo() : base.id() + "_" + v.etapa().ordinal() + "_" + v.etapa() + ".png";
                        return new ImagenVersionDTO(v.etapa().ordinal(), v.etapa(), nombre, "/almacen/" + nombre,
                                v.datos().length, v.hash());
                    })
                    .toList();
            dto = new MensajeDTO(base.id(), base.tipo(), base.canal(), base.remitente(), base.destinatario(),
                    base.fecha(), base.hash(), null, 0, 0, base.nombreArchivo(), versiones);
        }
        mensajes.add(dto);
        return dto;
    }

    /** Agrega un mensaje ya guardado (con la imagen ORIGINAL si es de imagen). */
    void sembrar(MensajeDTO m, byte[] original) {
        mensajes.add(m);
        if (m.tipo() == TipoMensaje.IMAGEN) {
            imagenes.put(m.id() + ":" + TipoFiltro.ORIGINAL, original);
        }
    }

    @Override
    public Optional<MensajeDTO> buscar(String id) {
        return mensajes.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    @Override
    public List<MensajeDTO> foro() {
        return ordenados(mensajes.stream().filter(m -> m.canal() == Canal.FORO).toList());
    }

    @Override
    public List<MensajeDTO> conversacion(String a, String b) {
        String clave = Mensaje.claveConversacion(a, b);
        return ordenados(mensajes.stream()
                .filter(m -> m.canal() == Canal.PRIVADO)
                .filter(m -> Mensaje.claveConversacion(m.remitente(), m.destinatario()).equals(clave))
                .toList());
    }

    @Override
    public List<MensajeDTO> todos() {
        return List.copyOf(mensajes);
    }

    @Override
    public Optional<byte[]> imagen(String id, TipoFiltro etapa) {
        return Optional.ofNullable(imagenes.get(id + ":" + etapa));
    }

    @Override
    public void cargarHistorial() {
        // Sin historial persistido en las pruebas.
    }

    private static List<MensajeDTO> ordenados(List<MensajeDTO> l) {
        return l.stream().sorted(Comparator.comparing(MensajeDTO::fecha)).toList();
    }
}
