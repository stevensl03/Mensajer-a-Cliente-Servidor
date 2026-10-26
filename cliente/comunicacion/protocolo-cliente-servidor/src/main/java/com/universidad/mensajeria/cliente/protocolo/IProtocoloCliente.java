package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Protocolo cliente-servidor (servidor §7): petición/respuesta asíncrona
 * correlacionada por {@code idPeticion} y eventos push.
 */
public interface IProtocoloCliente {

    /**
     * Abre la conexión y espera el push {@code INFO_SERVIDOR}. Bloquea como
     * máximo el timeout configurado: no se llama desde el hilo de la UI.
     *
     * @param host   host del servidor.
     * @param puerto puerto del servidor.
     * @return la IP del servidor según {@code INFO_SERVIDOR}; si no llega a
     *         tiempo, la IP remota del socket.
     * @throws java.io.UncheckedIOException si no se puede conectar.
     * @throws IllegalStateException        si la conexión se cierra antes de {@code INFO_SERVIDOR}.
     */
    String conectar(String host, int puerto);

    /**
     * @param c         comando.
     * @param params    parámetros (puede ser {@code null}).
     * @param contenido contenido del mensaje (puede ser {@code null}).
     * @return la respuesta del servidor. Falla con {@link java.util.concurrent.TimeoutException}
     *         si vence el timeout, o con {@link IllegalStateException} si no hay conexión o se pierde.
     *         Una respuesta con {@code exito = false} completa normalmente: la interpreta quien pide.
     */
    CompletableFuture<RespuestaDTO> solicitar(TipoComando c, Map<String, String> params, ContenidoDTO contenido);

    /** @param o oyente de los eventos push y de las desconexiones. */
    void suscribir(IOyentePush o);

    /** Cierra la conexión; las peticiones pendientes fallan y se avisa {@code onDesconexion}. */
    void desconectar();
}
