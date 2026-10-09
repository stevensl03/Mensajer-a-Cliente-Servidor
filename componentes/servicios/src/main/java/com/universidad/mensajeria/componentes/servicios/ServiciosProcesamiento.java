package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;

import java.io.IOException;
import java.util.Objects;

/**
 * Componente "Servicios": delega en los procesadores de texto e imagen.
 */
public final class ServiciosProcesamiento implements IServiciosDisponibles {

    private final ProcesadorTexto texto;
    private final ProcesadorImagen imagen;

    public ServiciosProcesamiento(ProcesadorTexto texto, ProcesadorImagen imagen) {
        this.texto = Objects.requireNonNull(texto, "texto");
        this.imagen = Objects.requireNonNull(imagen, "imagen");
    }

    @Override
    public ResultadoTextoDTO procesarTexto(String t) {
        return texto.procesar(t);
    }

    @Override
    public ResultadoImagenDTO procesarImagen(byte[] datos, String nombre) throws IOException {
        return imagen.procesar(datos, nombre);
    }
}
