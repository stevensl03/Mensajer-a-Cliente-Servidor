package com.universidad.mensajeria.dto;

/**
 * Código del resultado de una petición (contrato del protocolo).
 *
 * <p>Viaja por la red por nombre: no reordenar ni renombrar valores existentes;
 * los nuevos se agregan al final.</p>
 */
public enum CodigoError {
    OK,
    PETICION_INVALIDA,
    NO_AUTENTICADO,
    CREDENCIALES_INVALIDAS,
    CUPO_LLENO,
    DESTINO_INEXISTENTE,
    CONTENIDO_INVALIDO,
    COLA_LLENA,
    NO_ENCONTRADO,
    ERROR_INTERNO
}
