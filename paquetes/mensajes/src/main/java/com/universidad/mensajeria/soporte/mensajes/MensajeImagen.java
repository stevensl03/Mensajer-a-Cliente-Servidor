package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.TipoMensaje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensaje de imagen: la original y las imágenes generadas por los filtros.
 */
public final class MensajeImagen extends Mensaje {

    private final String nombreArchivo;
    private final byte[] datos;
    private final List<ImagenProcesadaDTO> imagenesFiltradas;

    public MensajeImagen(UUID id, Canal canal, String remitente, String destinatario,
                         LocalDateTime fechaEnvio, String hashSha256,
                         String nombreArchivo, byte[] datos,
                         List<ImagenProcesadaDTO> imagenesFiltradas) {
        super(id, canal, remitente, destinatario, fechaEnvio, hashSha256);
        this.nombreArchivo = Objects.requireNonNull(nombreArchivo, "nombreArchivo");
        this.datos = Objects.requireNonNull(datos, "datos").clone();
        this.imagenesFiltradas = List.copyOf(imagenesFiltradas);
    }

    @Override
    public TipoMensaje getTipo() {
        return TipoMensaje.IMAGEN;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    /** @return una copia de los bytes de la imagen original. */
    public byte[] getDatos() {
        return datos.clone();
    }

    public List<ImagenProcesadaDTO> getImagenesFiltradas() {
        return imagenesFiltradas;
    }
}
