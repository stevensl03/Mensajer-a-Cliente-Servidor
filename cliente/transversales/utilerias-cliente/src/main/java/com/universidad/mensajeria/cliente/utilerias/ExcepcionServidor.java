package com.universidad.mensajeria.cliente.utilerias;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.RespuestaDTO;

import java.util.Objects;

/**
 * El servidor respondió {@code exito = false} (D-16). Lleva el
 * {@link CodigoError} para que la presentación elija el texto
 * ({@code CUPO_LLENO}, {@code CREDENCIALES_INVALIDAS}…).
 */
public final class ExcepcionServidor extends RuntimeException {

    private final CodigoError codigo;

    /**
     * @param codigo  código de error del servidor.
     * @param mensaje mensaje del servidor.
     */
    public ExcepcionServidor(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    /** @return el código de error del servidor. */
    public CodigoError codigo() {
        return codigo;
    }

    /**
     * @param r respuesta recibida.
     * @return la misma respuesta si fue exitosa.
     * @throws ExcepcionServidor si {@code r.exito()} es falso.
     */
    public static RespuestaDTO exigirExito(RespuestaDTO r) {
        Objects.requireNonNull(r, "r");
        if (!r.exito()) {
            throw new ExcepcionServidor(r.codigo(), r.mensaje() == null ? r.codigo().name() : r.mensaje());
        }
        return r;
    }
}
