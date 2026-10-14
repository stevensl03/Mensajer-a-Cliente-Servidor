package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import com.universidad.mensajeria.fachada.ICanalCliente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integración con sockets reales en un puerto libre. El receptor es un eco:
 * aquí se prueba el transporte, no el protocolo.
 */
class ServidorTCPTest {

    /** Responde "eco:" + json y cuenta conexiones y desconexiones por canal. */
    private static final class ReceptorEco implements IReceptorConexiones {
        final AtomicInteger conectados = new AtomicInteger();
        final Map<String, AtomicInteger> desconexiones = new ConcurrentHashMap<>();

        @Override
        public void alConectar(ICanalCliente c) {
            conectados.incrementAndGet();
        }

        @Override
        public void alRecibir(ICanalCliente c, String json) {
            c.enviar("eco:" + json);
        }

        @Override
        public void alDesconectar(ICanalCliente c) {
            desconexiones.computeIfAbsent(c.getId(), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private final ReceptorEco receptor = new ReceptorEco();
    private ServidorTCP servidor;

    private int iniciar() throws IOException {
        servidor = new ServidorTCP(1024, 16);
        servidor.iniciar(0, receptor);
        return servidor.getPuertoLocal();
    }

    @AfterEach
    void detener() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void entregaLaTramaAlReceptorYEnviaLaRespuesta() throws IOException {
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("{\"a\":\"ñ\"}");
            assertEquals("eco:{\"a\":\"ñ\"}", cliente.recibir());
        }
        assertTrue(servidor.estaActivo());
    }

    @Test
    void atiendeVariosClientesALaVez() throws IOException {
        int puerto = iniciar();
        try (var a = new Cliente(puerto); var b = new Cliente(puerto)) {
            a.enviar("a");
            b.enviar("b");
            assertEquals("eco:a", a.recibir());
            assertEquals("eco:b", b.recibir());
            assertEquals(2, receptor.conectados.get());
        }
    }

    @Test
    void tramaInvalidaCierraLaConexionYDesconectaUnaVez() throws IOException {
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.out.writeInt(2048);
            cliente.out.flush();
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> receptor.desconexiones.size() == 1);
        receptor.desconexiones.values().forEach(n -> assertEquals(1, n.get()));
    }

    @Test
    void detenerCierraLasConexiones() throws IOException {
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("hola");
            cliente.recibir();

            servidor.detener();

            assertFalse(servidor.estaActivo());
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> receptor.desconexiones.size() == 1);
    }

    private static void esperarHasta(BooleanSupplier condicion) {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("La condición no se cumplió a tiempo");
            }
            Thread.onSpinWait();
        }
    }

    /** Cliente mínimo que habla el framing real: [int32 longitud][UTF-8]. */
    private static final class Cliente implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        Cliente(int puerto) throws IOException {
            socket = new Socket("127.0.0.1", puerto);
            socket.setSoTimeout(5_000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        }

        void enviar(String texto) throws IOException {
            byte[] datos = texto.getBytes(StandardCharsets.UTF_8);
            out.writeInt(datos.length);
            out.write(datos);
            out.flush();
        }

        String recibir() throws IOException {
            byte[] datos = new byte[in.readInt()];
            in.readFully(datos);
            return new String(datos, StandardCharsets.UTF_8);
        }

        boolean cerradoPorElServidor() throws IOException {
            try {
                in.readInt();
                return false;
            } catch (EOFException | SocketException e) {
                return true;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
