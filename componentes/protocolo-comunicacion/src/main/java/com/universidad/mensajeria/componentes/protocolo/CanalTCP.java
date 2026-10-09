package com.universidad.mensajeria.componentes.protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

/**
 * Canal sobre un socket TCP. Lee el hilo de {@link ManejadorCliente}; escribe
 * únicamente el hilo escritor de {@link CanalBase}.
 */
public final class CanalTCP extends CanalBase {

    private final Socket socket;
    private final CodificadorTramas tramas;
    private final DataInputStream entrada;
    private final DataOutputStream salida;

    /**
     * @param socket socket aceptado.
     * @param codec  codec de mensajes.
     * @param tramas delimitador de tramas.
     * @throws IOException si no se pueden obtener los flujos del socket.
     */
    public CanalTCP(Socket socket, ICodificadorMensajes codec, CodificadorTramas tramas) throws IOException {
        super(UUID.randomUUID().toString(), socket.getInetAddress().getHostAddress(), codec);
        this.socket = socket;
        this.tramas = tramas;
        // Sin comando de latido en el protocolo: el sistema operativo detecta pares caídos.
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        this.entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /** Lee la siguiente trama. Solo la usa el hilo lector. */
    byte[] leerTrama() throws IOException {
        return tramas.leer(entrada);
    }

    @Override
    protected void escribir(byte[] datos) throws IOException {
        tramas.escribir(salida, datos);
    }

    @Override
    protected void cerrarTransporte() {
        try {
            socket.close();
        } catch (IOException ignorada) {
            // El socket queda cerrado de todas formas.
        }
    }
}
