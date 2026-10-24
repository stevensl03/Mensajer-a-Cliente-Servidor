package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.dto.TipoFiltro;

import java.util.Arrays;

/** Servicios deterministas: el hash es "h-" + longitud; la imagen devuelve ORIGINAL + 5 versiones. */
final class ServiciosFalso implements IServiciosDisponibles {

    volatile byte[] ultimaImagen;
    volatile String ultimoNombre;
    volatile boolean fallar;

    @Override
    public ResultadoTextoDTO procesarTexto(String texto) {
        if (fallar) {
            throw new IllegalStateException("servicio caído");
        }
        return new ResultadoTextoDTO("h-" + texto.length(), texto.length(), texto.split("\\s+").length);
    }

    @Override
    public ResultadoImagenDTO procesarImagen(byte[] datos, String nombre) {
        if (fallar) {
            throw new IllegalArgumentException("no es una imagen");
        }
        ultimaImagen = datos.clone();
        ultimoNombre = nombre;
        var versiones = Arrays.stream(TipoFiltro.values())
                .map(f -> new ImagenProcesadaDTO(f, datos, "h" + f.ordinal()))
                .toList();
        return new ResultadoImagenDTO("h-" + datos.length, versiones);
    }
}
