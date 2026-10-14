package com.universidad.mensajeria.componentes.canal;

/**
 * JSON mal formado o petición incompleta. Forma parte del contrato
 * InterfazClienteServidor.
 */
public class PeticionInvalidaException extends RuntimeException {

    private final String idPeticion;

    /**
     * @param idPeticion id de la petición si se pudo leer, o {@code null}.
     * @param mensaje    descripción del problema.
     */
    public PeticionInvalidaException(String idPeticion, String mensaje) {
        super(mensaje);
        this.idPeticion = idPeticion;
    }

    /** @return id de la petición, o {@code null} si no se pudo leer. */
    public String getIdPeticion() {
        return idPeticion;
    }
}
