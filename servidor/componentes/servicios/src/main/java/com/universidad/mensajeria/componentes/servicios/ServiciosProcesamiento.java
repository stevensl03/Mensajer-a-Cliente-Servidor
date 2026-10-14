package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.componentes.servicios.imagen.IProcesadorImagen;
import com.universidad.mensajeria.componentes.servicios.texto.IProcesadorTexto;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;

import java.util.Objects;

/**
 * Clase principal del componente Servicios: solo delega en el subcomponente
 * que corresponde.
 */
public final class ServiciosProcesamiento implements IServiciosDisponibles {

    private final IProcesadorTexto texto;
    private final IProcesadorImagen imagen;

    public ServiciosProcesamiento(IProcesadorTexto texto, IProcesadorImagen imagen) {
        this.texto = Objects.requireNonNull(texto, "texto");
        this.imagen = Objects.requireNonNull(imagen, "imagen");
    }

    @Override
    public ResultadoTextoDTO procesarTexto(String t) {
        return texto.procesar(t);
    }

    @Override
    public ResultadoImagenDTO procesarImagen(byte[] datos, String nombre) {
        return imagen.procesar(datos, nombre);
    }
}
