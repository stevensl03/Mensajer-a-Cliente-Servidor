package com.universidad.mensajeria.cliente.servicios;

import com.universidad.mensajeria.cliente.protocolo.IOyentePush;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Protocolo en memoria: graba cada petición. Si hay una respuesta automática
 * para el comando, la usa; si no, la petición queda pendiente para que la
 * prueba la complete.
 */
public final class ProtocoloFalso implements IProtocoloCliente {

    private static final SerializadorJSON JSON = new SerializadorJSON();

    /** Una petición grabada. */
    public record Solicitud(TipoComando comando, Map<String, String> params, ContenidoDTO contenido,
                            CompletableFuture<RespuestaDTO> futuro) {
    }

    public final BlockingQueue<Solicitud> solicitudes = new LinkedBlockingQueue<>();
    private final Map<TipoComando, Function<Solicitud, RespuestaDTO>> automaticas = new EnumMap<>(TipoComando.class);

    /** Responde siempre lo mismo a un comando. */
    public ProtocoloFalso responder(TipoComando c, RespuestaDTO r) {
        return responder(c, s -> r);
    }

    public ProtocoloFalso responder(TipoComando c, Function<Solicitud, RespuestaDTO> f) {
        automaticas.put(c, f);
        return this;
    }

    @Override
    public String conectar(String host, int puerto) {
        return "127.0.0.1";
    }

    @Override
    public CompletableFuture<RespuestaDTO> solicitar(TipoComando c, Map<String, String> params, ContenidoDTO contenido) {
        var s = new Solicitud(c, params == null ? Map.of() : Map.copyOf(params), contenido, new CompletableFuture<>());
        solicitudes.add(s);
        var f = automaticas.get(c);
        if (f != null) {
            // Como el protocolo real: un fallo llega como futuro fallido, nunca como excepción.
            try {
                s.futuro().complete(f.apply(s));
            } catch (RuntimeException e) {
                s.futuro().completeExceptionally(e);
            }
        }
        return s.futuro();
    }

    @Override
    public void suscribir(IOyentePush o) {
        // Los servicios no reciben push directamente.
    }

    @Override
    public void desconectar() {
        // Nada que cerrar.
    }

    /** @return la siguiente petición grabada (espera hasta 5 s). */
    public Solicitud siguiente() throws InterruptedException {
        return solicitudes.poll(5, TimeUnit.SECONDS);
    }

    /** @return una respuesta exitosa con {@code datos} en JSON. */
    public static RespuestaDTO exito(String datos) {
        return new RespuestaDTO("p", true, CodigoError.OK, "OK", JSON.desdeJson(datos, com.fasterxml.jackson.databind.JsonNode.class));
    }

    /** @return una respuesta de error. */
    public static RespuestaDTO error(CodigoError c, String mensaje) {
        return new RespuestaDTO("p", false, c, mensaje, null);
    }
}
