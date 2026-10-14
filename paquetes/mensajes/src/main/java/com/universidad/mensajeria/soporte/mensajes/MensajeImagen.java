package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Mensaje de imagen: guarda la ORIGINAL y las 5 versiones filtradas.
 */
public final class MensajeImagen extends Mensaje {

    private final String nombreArchivo;
    private final List<ImagenProcesadaDTO> versiones;

    public MensajeImagen(String id, Canal canal, String remitente, String destinatario,
                         LocalDateTime fecha, String hash,
                         String nombreArchivo, List<ImagenProcesadaDTO> versiones) {
        super(id, canal, remitente, destinatario, fecha, hash);
        this.nombreArchivo = Objects.requireNonNull(nombreArchivo, "nombreArchivo");
        this.versiones = List.copyOf(versiones);
    }

    @Override
    public TipoMensaje tipo() {
        return TipoMensaje.IMAGEN;
    }

    /** @return la ORIGINAL y las 5 versiones filtradas, en orden. */
    public List<ImagenProcesadaDTO> versiones() {
        return versiones;
    }

    @Override
    public MensajeDTO aDTO() {
        // Las rutas de las versiones las asigna AlmacenarInformacion al guardar (fase 5).
        return new MensajeDTO(id, tipo(), canal, remitente, destinatario, fecha, hash,
                null, 0, 0, nombreArchivo, List.of());
    }
}
