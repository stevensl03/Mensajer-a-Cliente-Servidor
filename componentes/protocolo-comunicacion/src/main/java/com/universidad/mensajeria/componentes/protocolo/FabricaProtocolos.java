package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Crea el transporte configurado en {@code servidor.protocolo}. Agregar un
 * transporte es registrar su constructor aquí; nada más cambia.
 */
public final class FabricaProtocolos {

    private final Map<TipoProtocolo, BiFunction<IClienteServidor, IGestorConexiones, ServidorBase>> registro =
            new EnumMap<>(TipoProtocolo.class);

    /**
     * @param json          serializador para el codec JSON.
     * @param maxTramaBytes tamaño máximo de una trama TCP.
     */
    public FabricaProtocolos(ISerializador json, int maxTramaBytes) {
        Objects.requireNonNull(json, "json");
        registro.put(TipoProtocolo.TCP, (cs, gc) ->
                new ServidorTCP(cs, gc, new CodificadorJSON(json), new CodificadorTramas(maxTramaBytes)));
    }

    /**
     * @param tipo nombre del protocolo (TCP, UDP o GRPC, sin distinguir mayúsculas).
     * @param cs   lógica del protocolo cliente-servidor.
     * @param gc   gestor de conexiones.
     * @return el transporte, sin iniciar.
     * @throws IllegalArgumentException si el protocolo no existe o aún no está implementado.
     */
    public IProtocoloComunicacion crear(String tipo, IClienteServidor cs, IGestorConexiones gc) {
        TipoProtocolo protocolo;
        try {
            protocolo = TipoProtocolo.valueOf(Objects.requireNonNull(tipo, "tipo").strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Protocolo desconocido: " + tipo);
        }
        var constructor = registro.get(protocolo);
        if (constructor == null) {
            throw new IllegalArgumentException("Protocolo no disponible todavía: " + protocolo);
        }
        return constructor.apply(cs, gc);
    }
}
