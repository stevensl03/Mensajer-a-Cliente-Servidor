package com.universidad.mensajeria.componentes.servicios;

import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;

import java.io.IOException;

/**
 * Procesamiento de los mensajes recibidos (interfaz
 * "InterfazServiciosDisponibles" del diagrama).
 *
 * <p>No persiste ni habla con la red: recibe datos y devuelve resultados.</p>
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
     * Calcula el SHA-256 de la imagen y aplica secuencialmente los filtros.
     *
     * @param datos  bytes de la imagen original.
     * @param nombre nombre del archivo recibido.
     * @return la original y una imagen nueva por cada filtro.
     * @throws IOException si los bytes no son una imagen válida.
     */
    ResultadoImagenDTO procesarImagen(byte[] datos, String nombre) throws IOException;
}
