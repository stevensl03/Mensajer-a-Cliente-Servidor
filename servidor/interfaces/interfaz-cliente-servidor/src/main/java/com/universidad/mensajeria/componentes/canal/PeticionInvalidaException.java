package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.CodigoError;

import java.util.Objects;

/**
 * JSON mal formado, petición incompleta o contenido inválido. Forma parte del
 * contrato InterfazClienteServidor: quien la captura responde con
 * {@link #getCodigo()} y {@link #getIdPeticion()}.
 */
public class PeticionInvalidaException extends RuntimeException {

    private final String idPeticion;
    private final CodigoError codigo;

    /**
     * @param idPeticion id de la petición si se pudo leer, o {@code null}.
     * @param codigo     {@code PETICION_INVALIDA} o {@code CONTENIDO_INVALIDO}.
     * @param mensaje    descripción del problema (se envía al cliente).
     */
    public PeticionInvalidaException(String idPeticion, CodigoError codigo, String mensaje) {
        super(mensaje);
        this.idPeticion = idPeticion;
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    /** @return id de la petición, o {@code null} si no se pudo leer. */
    public String getIdPeticion() {
        return idPeticion;
    }

    /** @return código con el que se responde. */
    public CodigoError getCodigo() {
        return codigo;
    }
}
