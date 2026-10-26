package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.cliente.comunicacion.FabricaConexiones;
import com.universidad.mensajeria.cliente.comunicacion.TipoProtocolo;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integración con el transporte real: un servidor TCP en el puerto 0 que habla
 * el contrato (INFO_SERVIDOR al conectar, LOGIN → respuesta con el mismo id).
 */
class ProtocoloSobreTCPTest {

    private final SerializadorJSON json = new SerializadorJSON();

    @Test
    void conectaHaceLoginYRecibeLaRespuestaPorTcp() throws Exception {
        try (var servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var hilo = Thread.ofVirtual().start(() -> atender(servidor));
            IProtocoloCliente protocolo = new FabricaProtocoloCliente().crear(
                    new FabricaConexiones().crear(TipoProtocolo.TCP, 16_777_216), json, Duration.ofSeconds(5));

            String ip = protocolo.conectar(InetAddress.getLoopbackAddress().getHostAddress(), servidor.getLocalPort());
            RespuestaDTO r = protocolo.solicitar(TipoComando.LOGIN, Map.of("codigo", "20201001", "clave", "clave123"), null)
                    .get(5, TimeUnit.SECONDS);

            assertEquals("192.168.20.31", ip);
            assertTrue(r.exito());
            assertEquals("Ana", r.datos().path("usuario").path("nombres").asText());
            protocolo.desconectar();
            hilo.join(5_000);
        }
    }

    private void atender(ServerSocket servidor) {
        try (Socket s = servidor.accept()) {
            var entrada = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            var salida = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            escribir(salida, ConexionFalsa.INFO);
            byte[] b = new byte[entrada.readInt()];
            entrada.readFully(b);
            PeticionDTO p = json.desdeJson(new String(b, StandardCharsets.UTF_8), PeticionDTO.class);
            escribir(salida, "{\"idPeticion\":\"" + p.idPeticion() + "\",\"exito\":true,\"codigo\":\"OK\",\"mensaje\":\"OK\","
                    + "\"datos\":{\"idSesion\":\"s1\",\"ipServidor\":\"192.168.20.31\",\"usuario\":{\"codigo\":\"20201001\","
                    + "\"nombres\":\"Ana\",\"apellidos\":\"Pérez\",\"programa\":\"Sistemas\",\"rol\":\"USUARIO\"}}}");
            entrada.read(); // espera a que el cliente cierre
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void escribir(DataOutputStream salida, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        salida.writeInt(b.length);
        salida.write(b);
        salida.flush();
    }
}
