package com.universidad.mensajeria.cliente.comunicacion;

/**
 * Callback del transporte hacia la capa del protocolo. Se invoca desde el hilo
 * lector de la conexión: no debe bloquearse por mucho tiempo.
 */
public interface IReceptorServidor {

    /**
     * @param json contenido de una trama recibida (UTF-8 ya decodificado).
     */
    void alRecibir(String json);

    /**
     * Se llama una sola vez por conexión, tanto si la cerró el servidor o la red
     * como si la cerró el propio cliente con {@link IConexionServidor#cerrar()}.
     *
     * @param motivo descripción legible del cierre.
     */
    void alDesconectar(String motivo);
}
