package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.fachada.ICanalCliente;

/**
 * Callback que el transporte invoca por cada conexión. Lo implementa la
 * fachada; forma parte del contrato InterfazProtocoloComunicacion.
 */
public interface IReceptorConexiones {

    /** @param c canal recién aceptado. */
    void alConectar(ICanalCliente c);

    /**
     * @param c    canal por el que llegó la trama.
     * @param json contenido de la trama (JSON en UTF-8).
     */
    void alRecibir(ICanalCliente c, String json);

    /** @param c canal terminado. Se invoca una sola vez por canal. */
    void alDesconectar(ICanalCliente c);
}
