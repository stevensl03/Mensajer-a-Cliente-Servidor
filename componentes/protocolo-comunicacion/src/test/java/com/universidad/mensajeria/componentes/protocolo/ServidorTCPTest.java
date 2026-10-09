package com.universidad.mensajeria.componentes.protocolo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integración con sockets reales en un puerto libre. La lógica de comandos se
 * sustituye por un eco: aquí se prueba el transporte.
 */
class ServidorTCPTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Responde "eco COMANDO" a cualquier petición. */
    private static final IClienteServidor ECO = new IClienteServidor() {
        @Override
        public RespuestaDTO procesar(PeticionDTO p, ICanalCliente origen) {
            return RespuestaDTO.ok(p.idPeticion(), "eco " + p.comando(), null);
        }

        @Override
        public void notificar(EventoPushDTO e, Collection<ICanalCliente> destinos) {
            destinos.forEach(c -> c.push(e));
        }
    };

    private GestorConexiones gestor;
    private ServidorTCP servidor;

    private int iniciar(int maxSesiones) throws IOException {
        gestor = new GestorConexiones(maxSesiones);
        servidor = new ServidorTCP(ECO, gestor, new CodificadorJSON(new SerializadorJSON()),
                new CodificadorTramas(1024 * 1024));
        servidor.iniciar(0);
        return servidor.getPuertoLocal();
    }

    @AfterEach
    void detener() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void respondeConElMismoIdPeticion() throws IOException {
        int puerto = iniciar(5);
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("{\"idPeticion\":\"abc\",\"comando\":\"LOGIN\",\"parametros\":{}}");
            JsonNode respuesta = cliente.recibir();

            assertEquals("abc", respuesta.get("idPeticion").asText());
            assertTrue(respuesta.get("exito").asBoolean());
            assertEquals("eco LOGIN", respuesta.get("mensaje").asText());
        }
        assertTrue(servidor.estaActivo());
    }

    @Test
    void jsonInvalidoNoCierraLaConexion() throws IOException {
        int puerto = iniciar(5);
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("esto no es json");
            JsonNode error = cliente.recibir();
            assertFalse(error.get("exito").asBoolean());
            assertTrue(error.get("idPeticion").isNull());

            cliente.enviar("{\"idPeticion\":\"2\",\"comando\":\"LOGOUT\"}");
            assertTrue(cliente.recibir().get("exito").asBoolean());
        }
    }

    @Test
    void atiendeVariasSesionesALaVez() throws IOException {
        int puerto = iniciar(5);
        try (var a = new Cliente(puerto); var b = new Cliente(puerto)) {
            a.enviar("{\"idPeticion\":\"a\",\"comando\":\"LOGIN\"}");
            b.enviar("{\"idPeticion\":\"b\",\"comando\":\"LOGIN\"}");

            assertEquals("a", a.recibir().get("idPeticion").asText());
            assertEquals("b", b.recibir().get("idPeticion").asText());
            assertEquals(2, gestor.todas().size());
        }
    }

    @Test
    void conElCupoLlenoEnviaCupoLlenoYCierra() throws IOException {
        int puerto = iniciar(1);
        try (var primero = new Cliente(puerto); var segundo = new Cliente(puerto)) {
            primero.enviar("{\"idPeticion\":\"1\",\"comando\":\"LOGIN\"}");
            assertTrue(primero.recibir().get("exito").asBoolean());

            JsonNode push = segundo.recibir();
            assertEquals("CUPO_LLENO", push.get("tipo").asText());
            assertTrue(segundo.cerradoPorElServidor());
        }
    }

    @Test
    void elCupoSeLiberaCuandoElClienteSeVa() throws IOException {
        int puerto = iniciar(1);
        try (var primero = new Cliente(puerto)) {
            primero.enviar("{\"idPeticion\":\"1\",\"comando\":\"LOGIN\"}");
            primero.recibir();
        }
        esperarHasta(gestor::hayCupo);

        try (var segundo = new Cliente(puerto)) {
            segundo.enviar("{\"idPeticion\":\"2\",\"comando\":\"LOGIN\"}");
            assertTrue(segundo.recibir().get("exito").asBoolean());
        }
    }

    @Test
    void detenerCierraLasConexiones() throws IOException {
        int puerto = iniciar(5);
        try (var cliente = new Cliente(puerto)) {
            cliente.enviar("{\"idPeticion\":\"1\",\"comando\":\"LOGIN\"}");
            cliente.recibir();

            servidor.detener();

            assertFalse(servidor.estaActivo());
            assertTrue(cliente.cerradoPorElServidor());
        }
        esperarHasta(() -> gestor.todas().isEmpty());
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

    /** Cliente mínimo que habla el protocolo real: [int32 longitud][JSON UTF-8]. */
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

        void enviar(String json) throws IOException {
            byte[] datos = json.getBytes(StandardCharsets.UTF_8);
            out.writeInt(datos.length);
            out.write(datos);
            out.flush();
        }

        JsonNode recibir() throws IOException {
            byte[] datos = new byte[in.readInt()];
            in.readFully(datos);
            return MAPPER.readTree(datos);
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
