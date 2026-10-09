package com.universidad.mensajeria.utilerias;

/**
 * Calcula el hash de un bloque de bytes. Se inyecta en los componentes que lo
 * necesitan (inversión de dependencias).
 */
public interface IGeneradorHash {

    /**
     * @param datos bytes de entrada.
     * @return hash en hexadecimal (minúsculas).
     */
    String generar(byte[] datos);
}
