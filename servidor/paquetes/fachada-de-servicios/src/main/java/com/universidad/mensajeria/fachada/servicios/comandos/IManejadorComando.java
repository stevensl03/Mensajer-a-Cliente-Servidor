package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;

/**
 * Un caso de uso por comando del protocolo (patrón Command).
 */
@FunctionalInterface
public interface IManejadorComando {

    /**
     * @param p petición recibida.
     * @param c canal del remitente.
     * @return la respuesta para el cliente.
     */
    RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c);

    /**
     * @param p      petición recibida.
     * @param nombre parámetro obligatorio.
     * @return su valor (no en blanco).
     * @throws ExcepcionNegocio PETICION_INVALIDA si falta.
     */
    static String requerido(PeticionDTO p, String nombre) {
        return p.parametro(nombre).orElseThrow(
                () -> new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "Falta el parámetro " + nombre));
    }
}
