package com.universidad.mensajeria.cliente.comunicacion.tcp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Servidor TCP de prueba en un puerto libre de loopback; habla tramas {@code [int32][UTF-8]}. */
final class ServidorFalso implements AutoCloseable {

    private final ServerSocket servidor;
    private final List<Par> pares = new ArrayList<>();

    ServidorFalso() throws IOException {
        servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        servidor.setSoTimeout(5_000);
    }

    int puerto() {
        return servidor.getLocalPort();
    }

    /** @return la siguiente conexión aceptada (espera hasta 5 s). */
    Par aceptar() throws IOException {
        var par = new Par(servidor.accept());
        pares.add(par);
        return par;
    }

    @Override
    public void close() throws IOException {
        for (Par p : pares) {
            p.cerrar();
        }
        servidor.close();
    }

    /** Un cliente conectado, visto desde el servidor. */
    static final class Par {
        private final Socket socket;
        private final DataInputStream entrada;
        private final DataOutputStream salida;

        private Par(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(5_000);
            this.entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        void enviar(String json) throws IOException {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            salida.writeInt(b.length);
            salida.write(b);
            salida.flush();
        }

        /** Escribe solo una cabecera de longitud (para provocar tramas inválidas). */
        void enviarLongitud(int n) throws IOException {
            salida.writeInt(n);
            salida.flush();
        }

        String leer() throws IOException {
            byte[] b = new byte[entrada.readInt()];
            entrada.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }

        /** @return {@code true} si el cliente cerró el flujo (EOF) antes del plazo de lectura. */
        boolean finDeFlujo() throws IOException {
            return entrada.read() == -1;
        }

        void cerrar() {
            try {
                socket.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
