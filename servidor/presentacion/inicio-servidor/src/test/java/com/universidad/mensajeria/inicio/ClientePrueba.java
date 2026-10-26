package com.universidad.mensajeria.inicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.utilerias.ISerializador;
import com.universidad.mensajeria.utilerias.SerializadorJSON;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Cliente mínimo que habla el protocolo §7 por TCP: tramas
 * {@code [int32 big-endian][JSON UTF-8]}. Un hilo lee todas las tramas; las
 * respuestas se emparejan por {@code idPeticion} y los eventos push se guardan
 * para consultarlos después.
 */
final class ClientePrueba implements AutoCloseable {

    private static final long ESPERA_MS = 10_000;
    private static final JsonNode CERRADO = new SerializadorJSON().aArbol(Map.of("__cerrado", true));

    private final String nombre;
    private final ISerializador json = new SerializadorJSON();
    private final Socket socket;
    private final DataOutputStream out;
    private final BlockingQueue<JsonNode> entrantes = new LinkedBlockingQueue<>();
    private final List<JsonNode> pushPendientes = new ArrayList<>();
    private volatile boolean cerrado;

    ClientePrueba(String nombre, int puerto) throws IOException {
        this.nombre = nombre;
        this.socket = new Socket("127.0.0.1", puerto);
        this.out = new DataOutputStream(socket.getOutputStream());
        var in = new DataInputStream(socket.getInputStream());
        Thread.ofVirtual().name("lector-" + nombre).start(() -> leer(in));
    }

    /** Envía una petición y devuelve su respuesta (los push que lleguen mientras tanto se guardan). */
    JsonNode pedir(TipoComando comando, Map<String, String> parametros, ContenidoDTO contenido) throws IOException {
        String id = UUID.randomUUID().toString();
        byte[] datos = json.aJson(new PeticionDTO(id, comando, parametros, contenido)).getBytes(StandardCharsets.UTF_8);
        out.writeInt(datos.length);
        out.write(datos);
        out.flush();
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            JsonNode t = siguiente(limite);
            if (t == CERRADO) {
                throw new IllegalStateException(nombre + ": el servidor cerró el canal esperando " + comando);
            }
            if (t != null && t.has("evento")) {
                pushPendientes.add(t);
            } else if (t != null && id.equals(t.path("idPeticion").asText())) {
                return t;
            }
        }
        throw new AssertionError(nombre + ": sin respuesta a " + comando);
    }

    JsonNode pedir(TipoComando comando, Map<String, String> parametros) throws IOException {
        return pedir(comando, parametros, null);
    }

    /** @return los {@code datos} del primer push de ese tipo que cumpla la condición. */
    JsonNode esperarPush(String evento, Predicate<JsonNode> condicion) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (true) {
            for (Iterator<JsonNode> it = pushPendientes.iterator(); it.hasNext(); ) {
                JsonNode p = it.next();
                if (p.path("evento").asText().equals(evento) && condicion.test(p.path("datos"))) {
                    it.remove();
                    return p.path("datos");
                }
            }
            JsonNode t = siguiente(limite);
            if (t == null || t == CERRADO) {
                throw new AssertionError(nombre + ": no llegó el push " + evento);
            }
            if (t.has("evento")) {
                pushPendientes.add(t);
            }
        }
    }

    JsonNode esperarPush(String evento) {
        return esperarPush(evento, d -> true);
    }

    /** @return {@code true} si el servidor cerró el canal dentro del plazo. */
    boolean cerradoPorElServidor() {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (!cerrado && System.currentTimeMillis() < limite) {
            JsonNode t = siguiente(limite);
            if (t == CERRADO) {
                return true;
            }
            if (t != null && t.has("evento")) {
                pushPendientes.add(t);
            }
        }
        return cerrado;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private JsonNode siguiente(long limite) {
        try {
            return entrantes.poll(Math.max(1, limite - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void leer(DataInputStream in) {
        try {
            while (true) {
                byte[] datos = new byte[in.readInt()];
                in.readFully(datos);
                entrantes.add(json.desdeJson(new String(datos, StandardCharsets.UTF_8), JsonNode.class));
            }
        } catch (EOFException | java.net.SocketException e) {
            // Fin normal: el servidor (o el propio cliente) cerró el socket.
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            cerrado = true;
            entrantes.add(CERRADO);
        }
    }
}
