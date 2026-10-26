package com.universidad.mensajeria.cliente.inicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.fachada.IObservadorCliente;
import com.universidad.mensajeria.cliente.utilerias.ConfiguracionCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La pila completa del cliente (sin JavaFX) tal como la ensambla AppCliente,
 * contra un servidor TCP falso que habla el contrato de servidor §7.
 */
class AppClienteEnsambladoTest {

    private static final SerializadorJSON JSON = new SerializadorJSON();
    private static final String HASH = "c".repeat(64);

    @TempDir
    Path dir;

    @Test
    void conectaEntraRecibeGuardaEnH2YSale() throws Exception {
        try (var servidor = new ServidorContrato()) {
            IFachadaCliente fachada = AppCliente.ensamblar(configuracion(servidor.puerto()));
            var observador = new Observador();
            fachada.suscribir(observador);

            String ip = fachada.conectar("127.0.0.1", servidor.puerto());
            var malo = assertThrows(ExecutionException.class, () -> fachada.login("20201001", "mala").get(5, TimeUnit.SECONDS));
            assertEquals(CodigoError.CREDENCIALES_INVALIDAS,
                    assertInstanceOf(ExcepcionServidor.class, malo.getCause()).codigo());
            fachada.conectar("127.0.0.1", servidor.puerto());
            fachada.login("20201001", "clave123").get(5, TimeUnit.SECONDS);
            servidor.push("{\"evento\":\"MENSAJE_NUEVO\",\"fecha\":\"2026-10-25T20:00:00.123456789\",\"datos\":{\"mensaje\":"
                    + "{\"id\":\"m1\",\"tipo\":\"TEXTO\",\"canal\":\"FORO\",\"remitente\":\"20201002\","
                    + "\"fecha\":\"2026-10-25T20:00:00.120000001\",\"hash\":\"" + HASH + "\",\"texto\":\"Hola foro\","
                    + "\"numCaracteres\":9,\"numPalabras\":2,\"imagenes\":[]}}}");
            MensajeDTO recibido = observador.mensajes.poll(5, TimeUnit.SECONDS);

            assertEquals("192.168.20.31", ip);
            assertEquals(1, servidor.conexiones, "el login fallido reutilizó la conexión (D-06)");
            assertEquals("Ana", fachada.usuarioActual().orElseThrow().nombres());
            assertTrue(fachada.sesionLocal() > 0);
            assertEquals("Hola foro", recibido.texto());
            assertEquals(1, fachada.mensajesGuardados());
            assertEquals(List.of("m1"), fachada.historial("FORO").stream().map(MensajeDTO::id).toList());
            assertTrue(Files.exists(dir.resolve("data/cliente_20201001.mv.db")));

            fachada.logout();

            assertTrue(servidor.comandos.contains("LOGOUT"));
            assertTrue(fachada.usuarioActual().isEmpty());
            Thread.sleep(200);
            assertTrue(observador.desconexiones.isEmpty(), "salir no es una caída (R-31)");
        }
    }

    private ConfiguracionCliente configuracion(int puerto) throws IOException {
        Path archivo = dir.resolve("cliente.properties");
        Files.writeString(archivo, """
                servidor.host=127.0.0.1
                servidor.puerto=%d
                servidor.protocolo=TCP
                servidor.maxTramaBytes=16777216
                peticion.timeoutSegundos=5
                h2.url=jdbc:h2:%s/data/cliente_{codigo}
                h2.usuario=sa
                h2.clave=
                cache.mensajes.max=50
                """.formatted(puerto, dir.toAbsolutePath()), StandardCharsets.UTF_8);
        return ConfiguracionCliente.cargar(archivo);
    }

    /** Servidor mínimo: INFO_SERVIDOR al conectar, LOGIN, HISTORIAL vacío y LOGOUT (que cierra el canal). */
    private static final class ServidorContrato implements AutoCloseable {
        private final ServerSocket servidor = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        final List<String> comandos = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile int conexiones;
        private volatile DataOutputStream salida;

        ServidorContrato() throws IOException {
            Thread.ofVirtual().start(this::aceptar);
        }

        int puerto() {
            return servidor.getLocalPort();
        }

        void push(String json) throws IOException {
            escribir(salida, json);
        }

        private void aceptar() {
            while (!servidor.isClosed()) {
                try {
                    Socket s = servidor.accept();
                    conexiones++;
                    Thread.ofVirtual().start(() -> atender(s));
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void atender(Socket s) {
            try (s) {
                var entrada = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                var out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                salida = out;
                escribir(out, "{\"evento\":\"INFO_SERVIDOR\",\"fecha\":\"2026-10-25T20:00:00\",\"datos\":"
                        + "{\"ipServidor\":\"192.168.20.31\",\"puerto\":5000,\"protocolo\":\"TCP\",\"cuposLibres\":10}}");
                while (true) {
                    byte[] b = new byte[entrada.readInt()];
                    entrada.readFully(b);
                    JsonNode p = JSON.desdeJson(new String(b, StandardCharsets.UTF_8), JsonNode.class);
                    String id = p.get("idPeticion").asText();
                    String comando = p.get("comando").asText();
                    comandos.add(comando);
                    switch (comando) {
                        case "LOGIN" -> escribir(out, "mala".equals(p.path("parametros").path("clave").asText())
                                ? error(id, "CREDENCIALES_INVALIDAS")
                                : exito(id, "{\"idSesion\":\"s1\",\"ipServidor\":\"192.168.20.31\",\"usuario\":"
                                + "{\"codigo\":\"20201001\",\"nombres\":\"Ana\",\"apellidos\":\"Pérez\","
                                + "\"programa\":\"Sistemas\",\"rol\":\"USUARIO\"}}"));
                        case "HISTORIAL" -> escribir(out, exito(id, "{\"mensajes\":[]}"));
                        case "LOGOUT" -> {
                            escribir(out, exito(id, null));
                            return;
                        }
                        default -> escribir(out, error(id, "PETICION_INVALIDA"));
                    }
                }
            } catch (EOFException e) {
                // El cliente cerró la conexión.
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String exito(String id, String datos) {
            return "{\"idPeticion\":\"" + id + "\",\"exito\":true,\"codigo\":\"OK\",\"mensaje\":\"OK\""
                    + (datos == null ? "" : ",\"datos\":" + datos) + "}";
        }

        private static String error(String id, String codigo) {
            return "{\"idPeticion\":\"" + id + "\",\"exito\":false,\"codigo\":\"" + codigo + "\",\"mensaje\":\"rechazado\"}";
        }

        private static synchronized void escribir(DataOutputStream out, String json) throws IOException {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            out.writeInt(b.length);
            out.write(b);
            out.flush();
        }

        @Override
        public void close() throws IOException {
            servidor.close();
        }
    }

    private static final class Observador implements IObservadorCliente {
        final BlockingQueue<MensajeDTO> mensajes = new LinkedBlockingQueue<>();
        final List<String> desconexiones = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onMensaje(MensajeDTO m) {
            mensajes.add(m);
        }

        @Override
        public void onUsuarios(List<EstadoUsuarioDTO> l) {
            // No se usa en esta prueba.
        }

        @Override
        public void onDesconexion(String motivo) {
            desconexiones.add(motivo);
        }
    }
}
