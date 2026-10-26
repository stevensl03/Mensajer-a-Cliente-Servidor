package com.universidad.mensajeria.cliente.comunicacion;

/**
 * Transporte hacia el servidor (hoy TCP). Es lo único que conoce sockets: para
 * cambiar de protocolo se agrega otra implementación (cliente §9).
 */
public interface IConexionServidor {

    /**
     * Abre la conexión y empieza a entregar las tramas a {@code r}. Después de
     * {@link #cerrar()} (o de una caída) se puede volver a llamar: abre una
     * conexión nueva (D-06).
     *
     * @param host   host del servidor.
     * @param puerto puerto del servidor.
     * @param r      receptor de tramas y del aviso de desconexión.
     * @throws java.io.UncheckedIOException si no se puede conectar.
     * @throws IllegalStateException        si ya hay una conexión abierta.
     */
    void conectar(String host, int puerto, IReceptorServidor r);

    /**
     * Encola un mensaje; no bloquea esperando la red.
     *
     * @param json mensaje a enviar.
     * @throws IllegalStateException    si no hay conexión o la cola de salida está llena.
     * @throws IllegalArgumentException si el mensaje supera el tamaño máximo de trama.
     */
    void enviar(String json);

    /** Entrega lo ya encolado y cierra la conexión. Idempotente. */
    void cerrar();

    /** @return la IP remota de la última conexión, o {@code null} si nunca se conectó. */
    String getIpServidor();

    /** @return {@code true} si hay una conexión abierta que no se está cerrando. */
    boolean estaConectada();
}
