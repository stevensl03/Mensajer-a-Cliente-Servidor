package com.universidad.mensajeria.componentes.servicios.texto;

import com.universidad.mensajeria.dto.ResultadoTextoDTO;

/**
 * Subcomponente ProcesamientoTexto.
 */
public interface IProcesadorTexto {

    /**
     * @param texto texto recibido.
     * @return hash SHA-256, caracteres y palabras.
     */
    ResultadoTextoDTO procesar(String texto);
}
