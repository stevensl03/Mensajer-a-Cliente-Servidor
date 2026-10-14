package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.PeticionDTO;

/**
 * Parámetros obligatorios por comando y límites de contenido.
 */
public final class ValidadorPeticiones {

    private final int maxTextoCaracteres;
    private final int maxImagenBytes;

    public ValidadorPeticiones(int maxTextoCaracteres, int maxImagenBytes) {
        this.maxTextoCaracteres = maxTextoCaracteres;
        this.maxImagenBytes = maxImagenBytes;
    }

    /**
     * @param p petición a validar.
     * @throws PeticionInvalidaException si faltan parámetros o el contenido no es válido.
     */
    public void validar(PeticionDTO p) {
        throw new UnsupportedOperationException("Fase 4d");
    }
}
