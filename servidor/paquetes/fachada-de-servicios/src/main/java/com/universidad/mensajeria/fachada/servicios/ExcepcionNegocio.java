package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.CodigoError;

import java.util.Objects;

/**
 * Error esperado de un caso de uso. Se traduce a una {@code RespuestaDTO} con
 * su código.
 */
public final class ExcepcionNegocio extends RuntimeException {

    private final CodigoError codigo;

    /**
     * @param codigo  código que viaja en la respuesta (distinto de OK).
     * @param mensaje descripción legible para el cliente.
     */
    public ExcepcionNegocio(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        if (codigo == CodigoError.OK) {
            throw new IllegalArgumentException("Una excepción de negocio no puede tener el código OK");
        }
    }

    public CodigoError getCodigo() {
        return codigo;
    }
}
