package com.universidad.mensajeria.cliente.protocolo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.cliente.comunicacion.IConexionServidor;
import com.universidad.mensajeria.cliente.comunicacion.IReceptorServidor;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEventoPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JSON del protocolo (servidor §7) sobre un {@link IConexionServidor}; no
 * conoce sockets.
 *
 * <p>Cada petición lleva un {@code idPeticion} (UUID) y un
 * {@link CompletableFuture} en {@code pendientes}, que se completa cuando
 * llega la respuesta con el mismo id o vence el timeout. Una trama con
 * {@code evento} es un push; las demás son respuestas (una respuesta puede
 * traer {@code idPeticion: null}, R-36).</p>
 */
public final class ClienteProtocoloJSON implements IProtocoloCliente, IReceptorServidor {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteProtocoloJSON.class);
    private static final TypeReference<EventoPushDTO> EVENTO = new TypeReference<>() { };
    private static final TypeReference<RespuestaDTO> RESPUESTA = new TypeReference<>() { };

    private final IConexionServidor conexion;
    private final ISerializador json;
    private final ConcurrentHashMap<String, CompletableFuture<RespuestaDTO>> pendientes = new ConcurrentHashMap<>();
    private final DespachadorEventos despachador = new DespachadorEventos();
    private final Duration timeout;
    /** IP que trae {@code INFO_SERVIDOR}; se renueva en cada {@link #conectar}. */
    private volatile CompletableFuture<String> infoServidor = new CompletableFuture<>();

    /**
     * Se crea con {@link FabricaProtocoloCliente}.
     *
     * @param conexion transporte.
     * @param json     serializador.
     * @param timeout  espera máxima de una respuesta (y de {@code INFO_SERVIDOR}).
     */
    ClienteProtocoloJSON(IConexionServidor conexion, ISerializador json, Duration timeout) {
        this.conexion = Objects.requireNonNull(conexion, "conexion");
        this.json = Objects.requireNonNull(json, "json");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("El timeout debe ser positivo: " + timeout);
        }
    }

    @Override
    public String conectar(String host, int puerto) {
        var info = new CompletableFuture<String>();
        infoServidor = info;
        conexion.conectar(host, puerto, this);
        try {
            return info.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("No llegó INFO_SERVIDOR en {} s; se usa la IP del socket", timeout.toSeconds());
            return conexion.getIpServidor();
        } catch (ExecutionException e) {
            throw new IllegalStateException("La conexión con " + host + ":" + puerto
                    + " se cerró antes de INFO_SERVIDOR: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return conexion.getIpServidor();
        }
    }

    @Override
    public CompletableFuture<RespuestaDTO> solicitar(TipoComando c, Map<String, String> params, ContenidoDTO contenido) {
        Objects.requireNonNull(c, "c");
        String id = UUID.randomUUID().toString();
        var futuro = new CompletableFuture<RespuestaDTO>();
        pendientes.put(id, futuro);
        try {
            conexion.enviar(json.aJson(new PeticionDTO(id, c, params, contenido)));
        } catch (RuntimeException e) {
            pendientes.remove(id);
            return CompletableFuture.failedFuture(e);
        }
        futuro.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((r, e) -> pendientes.remove(id, futuro));
        return futuro;
    }

    @Override
    public void suscribir(IOyentePush o) {
        despachador.suscribir(o);
    }

    @Override
    public void desconectar() {
        conexion.cerrar();
    }

    /** Respuesta si no trae {@code evento}; push si lo trae. Una trama ilegible se descarta. */
    @Override
    public void alRecibir(String texto) {
        JsonNode arbol;
        try {
            arbol = json.desdeJson(texto, JsonNode.class);
        } catch (IllegalArgumentException e) {
            LOG.warn("Trama del servidor con JSON mal formado: {}", e.getMessage());
            return;
        }
        if (arbol == null || !arbol.isObject()) {
            LOG.warn("Trama del servidor que no es un objeto JSON");
            return;
        }
        if (arbol.hasNonNull("evento")) {
            recibirEvento(arbol);
        } else {
            recibirRespuesta(arbol);
        }
    }

    /** Falla todas las pendientes y avisa a los oyentes. */
    @Override
    public void alDesconectar(String motivo) {
        var causa = new IllegalStateException("Desconectado del servidor: " + motivo);
        infoServidor.completeExceptionally(causa);
        for (String id : pendientes.keySet()) {
            CompletableFuture<RespuestaDTO> f = pendientes.remove(id);
            if (f != null) {
                f.completeExceptionally(causa);
            }
        }
        despachador.desconexion(motivo);
    }

    /** @return cuántas peticiones esperan respuesta (para las pruebas). */
    int pendientes() {
        return pendientes.size();
    }

    private void recibirEvento(JsonNode arbol) {
        EventoPushDTO e;
        try {
            e = json.convertir(arbol, EVENTO);
        } catch (IllegalArgumentException ex) {
            LOG.warn("Evento push con formato inválido: {}", ex.getMessage());
            return;
        }
        if (e.evento() == TipoEventoPush.INFO_SERVIDOR && e.datos() != null) {
            String ip = e.datos().path("ipServidor").asText(null);
            if (ip != null) {
                infoServidor.complete(ip);
            }
        }
        despachador.despachar(e);
    }

    private void recibirRespuesta(JsonNode arbol) {
        JsonNode nodoId = arbol.get("idPeticion");
        String id = nodoId != null && nodoId.isTextual() ? nodoId.asText() : null;
        CompletableFuture<RespuestaDTO> f = id == null ? null : pendientes.remove(id);
        if (f == null) {
            LOG.warn("Respuesta sin petición pendiente (idPeticion={}): {}", id,
                    arbol.path("mensaje").asText(""));
            return;
        }
        try {
            f.complete(json.convertir(arbol, RESPUESTA));
        } catch (IllegalArgumentException e) {
            f.completeExceptionally(new IllegalStateException("Respuesta del servidor con formato inválido", e));
        }
    }
}
