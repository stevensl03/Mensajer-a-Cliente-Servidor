package com.universidad.mensajeria.componentes.protocolo;

/**
 * Convierte los mensajes del protocolo entre objetos y bytes.
 */
public interface ICodificadorMensajes {

    /**
     * @param o mensaje a codificar.
     * @return los bytes del mensaje.
     */
    byte[] codificar(Object o);

    /**
     * @param b    bytes recibidos.
     * @param tipo clase del mensaje esperado.
     * @param <T>  tipo del mensaje.
     * @return el mensaje leído.
     * @throws ProtocoloException si los bytes no son un mensaje válido de ese tipo.
     */
    <T> T decodificar(byte[] b, Class<T> tipo) throws ProtocoloException;
}
