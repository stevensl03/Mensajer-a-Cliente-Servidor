package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;

/**
 * Procesamiento de los mensajes recibidos (InterfazServiciosDisponibles).
 * No persiste ni habla con la red: recibe datos y devuelve resultados.
 */
public interface IServiciosDisponibles {

    /**
     * Calcula SHA-256, número de caracteres y número de palabras.
     *
     * @param texto texto recibido.
     * @return el resultado del procesamiento.
     */
    ResultadoTextoDTO procesarTexto(String texto);

    /**
     * Calcula el SHA-256 de la imagen y aplica secuencialmente los 5 filtros.
     *
     * @param datos  bytes de la imagen original.
     * @param nombre nombre del archivo recibido.
     * @return la original y una imagen nueva por cada filtro.
     * @throws IllegalArgumentException si los bytes no son una imagen válida.
     */
    ResultadoImagenDTO procesarImagen(byte[] datos, String nombre);
}
