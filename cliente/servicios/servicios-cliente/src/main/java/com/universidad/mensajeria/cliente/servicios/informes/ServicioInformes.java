package com.universidad.mensajeria.cliente.servicios.informes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoInforme;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Componente Informes (cliente §5.4). */
public final class ServicioInformes implements IServicioInformes {

    private static final TypeReference<InformeDTO> INFORME = new TypeReference<>() { };

    private final IProtocoloCliente protocolo;
    private final ISerializador json;

    public ServicioInformes(IProtocoloCliente protocolo, ISerializador json) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public CompletableFuture<InformeRecibido> obtener(TipoInforme t, Map<String, String> filtros) {
        Objects.requireNonNull(t, "t");
        var params = new HashMap<String, String>();
        if (filtros != null) {
            filtros.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    params.put(k, v.strip());
                }
            });
        }
        params.put("tipo", t.name());
        return protocolo.solicitar(TipoComando.INFORME, params, null).thenApply(r -> {
            JsonNode datos = ExcepcionServidor.exigirExito(r).datos();
            return new InformeRecibido(json.convertir(datos, INFORME), datos.toPrettyString());
        });
    }
}
