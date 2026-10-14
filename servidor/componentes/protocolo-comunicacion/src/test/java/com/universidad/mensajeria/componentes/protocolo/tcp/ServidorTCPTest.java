package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import com.universidad.mensajeria.fachada.ICanalCliente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integración con sockets reales en el puerto 0. El receptor es configurable:
 * aquí se prueba el transporte, no el protocolo.
 */
@Timeout(30)
class ServidorTCPTest {

    /** Por defecto responde "eco:" + json; cuenta conexiones y desconexiones por canal. */
    private static final class Receptor implements IReceptorConexiones {
        final AtomicInteger conectados = new AtomicInteger();
        final Map<String, AtomicInteger> desconexiones = new ConcurrentHashMap<>();
        final BlockingQueue<ICanalCliente> canales = new LinkedBlockingQueue<>();
        volatile BiConsumer<ICanalCliente, String> alRecibir = (c, json) -> c.enviar("eco:" + json);

        @Override
        public void alConectar(ICanalCliente c) {
            conectados.incrementAndGet();
            canales.add(c);
        }

        @Override
        public void alRecibir(ICanalCliente c, String json) {
            alRecibir.accept(c, json);
        }

        @Override
        public void alDesconectar(ICanalCliente c) {
            desconexiones.computeIfAbsent(c.getId(), k -> new AtomicInteger()).incrementAndGet();
        }

        ICanalCliente siguienteCanal() throws InterruptedException {
            ICanalCliente c = canales.poll(5, TimeUnit.SECONDS);
            assertNotNull(c, "el servidor no aceptó la conexión");
            return c;
        }
    }

    private final Receptor receptor = new Receptor();
    private ServidorTCP servidor;

    private int iniciar(int maxTrama, int colaSalida) throws IOException {
        servidor = new ServidorTCP(maxTrama, colaSalida);
        servidor.iniciar(0, receptor);
        return servidor.getPuertoLocal();
    }

    private int iniciar() throws IOException {
        return iniciar(1024, 16);
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
            cliente.enviar("segunda");
            assertEquals("eco:segunda", cliente.recibir());
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
    void cadaCanalTieneUnIdDistintoYLaIpRemota() throws Exception {
        int puerto = iniciar();
        try (var a = new Cliente(puerto); var b = new Cliente(puerto)) {
            ICanalCliente ca = receptor.siguienteCanal();
            ICanalCliente cb = receptor.siguienteCanal();
            assertFalse(ca.getId().equals(cb.getId()));
            assertEquals("127.0.0.1", ca.getIpRemota());
            assertTrue(ca.estaAbierto());
        }
    }

    @Test
    void tramaInvalidaCierraLaConexionYDesconectaUnaVez() throws IOException {
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.out.writeInt(2048); // supera maxTramaBytes = 1024
            cliente.out.flush();
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> receptor.desconexiones.size() == 1);
        assertEquals(1, receptor.desconexiones.values().iterator().next().get());
    }

    @Test
    void elClienteSeVaYSeDesconectaUnaVez() throws Exception {
        int puerto = iniciar();
        var cliente = new Cliente(puerto);
        ICanalCliente canal = receptor.siguienteCanal();

        cliente.close();

        esperarHasta(() -> receptor.desconexiones.containsKey(canal.getId()));
        Thread.sleep(200);
        assertEquals(1, receptor.desconexiones.get(canal.getId()).get());
        assertFalse(canal.estaAbierto());
    }

    @Test
    void cerrarDesdeElReceptorEntregaLoPendienteYDesconectaUnaVez() throws IOException {
        receptor.alRecibir = (c, json) -> {
            c.enviar("adios");
            c.cerrar();
            c.enviar("se descarta");
        };
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("{\"comando\":\"LOGOUT\"}");
            assertEquals("adios", cliente.recibir());
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> receptor.desconexiones.size() == 1);
        assertEquals(1, receptor.desconexiones.values().iterator().next().get());
    }

    @Test
    void unUnicoEscritorAunqueEscribanMuchosHilos() throws Exception {
        int hilos = 8;
        int porHilo = 200;
        int puerto = iniciar(1024, hilos * porHilo);
        try (var cliente = new Cliente(puerto)) {
            ICanalCliente canal = receptor.siguienteCanal();
            var salida = new CountDownLatch(1);
            var productores = new ArrayList<Thread>();
            for (int h = 0; h < hilos; h++) {
                int id = h;
                productores.add(Thread.ofVirtual().start(() -> {
                    esperar(salida);
                    for (int n = 0; n < porHilo; n++) {
                        canal.enviar("{\"hilo\":" + id + ",\"n\":" + n + ",\"relleno\":\"" + "x".repeat(50) + "\"}");
                    }
                }));
            }
            salida.countDown();

            // Cada trama llega completa y, por hilo, en el orden en que se envió.
            Map<Integer, Integer> ultimo = new HashMap<>();
            for (int i = 0; i < hilos * porHilo; i++) {
                String json = cliente.recibir();
                int hilo = Integer.parseInt(json.replaceAll(".*\"hilo\":(\\d+),.*", "$1"));
                int n = Integer.parseInt(json.replaceAll(".*\"n\":(\\d+),.*", "$1"));
                assertTrue(json.endsWith("x".repeat(50) + "\"}"), "trama corrupta: " + json);
                assertEquals(ultimo.getOrDefault(hilo, -1) + 1, n, "orden del hilo " + hilo);
                ultimo.put(hilo, n);
            }
            for (Thread t : productores) {
                t.join();
            }
            assertTrue(canal.estaAbierto());
        }
    }

    @Test
    void clienteLentoColaDeSalidaLlenaCierraElCanal() throws Exception {
        int maxTrama = 1024 * 1024;
        int puerto = iniciar(maxTrama, 2);
        try (var cliente = new Cliente(puerto)) { // nunca lee
            ICanalCliente canal = receptor.siguienteCanal();
            String grande = "x".repeat(maxTrama - 10);

            int enviados = 0;
            while (canal.estaAbierto() && enviados < 500) {
                canal.enviar(grande);
                enviados++;
            }

            assertFalse(canal.estaAbierto(), "el canal se cierra cuando la cola se llena");
            assertTrue(enviados < 500);
            esperarHasta(() -> receptor.desconexiones.containsKey(canal.getId()));
            assertEquals(1, receptor.desconexiones.get(canal.getId()).get());
        }
    }

    @Test
    void unMensajeDeSalidaDemasiadoGrandeSeDescartaSinCerrar() throws IOException {
        receptor.alRecibir = (c, json) -> {
            c.enviar("x".repeat(5000)); // supera maxTramaBytes = 1024
            c.enviar("siguiente");
        };
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("hola");
            assertEquals("siguiente", cliente.recibir());
        }
    }

    @Test
    void unErrorDelReceptorNoCierraLaConexion() throws IOException {
        receptor.alRecibir = (c, json) -> {
            if (json.equals("falla")) {
                throw new IllegalStateException("fallo simulado");
            }
            c.enviar("ok:" + json);
        };
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("falla");
            cliente.enviar("bien");
            assertEquals("ok:bien", cliente.recibir());
        }
    }

    @Test
    void detenerCierraLasConexionesYEsIdempotente() throws IOException {
        int puerto = iniciar();
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("hola");
            cliente.recibir();

            servidor.detener();
            servidor.detener();

            assertFalse(servidor.estaActivo());
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> receptor.desconexiones.size() == 1);
    }

    private static void esperarHasta(BooleanSupplier condicion) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail("La condición no se cumplió a tiempo");
            }
            Thread.onSpinWait();
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Cliente mínimo que habla el framing real: [int32 longitud][UTF-8]. */
    private static final class Cliente implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        Cliente(int puerto) throws IOException {
            socket = new Socket("127.0.0.1", puerto);
            socket.setSoTimeout(10_000);
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
