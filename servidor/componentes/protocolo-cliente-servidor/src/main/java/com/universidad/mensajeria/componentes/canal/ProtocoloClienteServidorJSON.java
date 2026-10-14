package com.universidad.mensajeria.componentes.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.Objects;

/**
 * Implementación JSON (Jackson) de {@link IClienteServidor}. No conoce
 * sockets ni casos de uso.
 *
 * <p>Formato (§7): fechas ISO-8601 sin zona, enums por nombre y nulos
 * omitidos, salvo {@code idPeticion} en las respuestas, que siempre aparece
 * (con {@code null} si la petición no se pudo leer).</p>
 */
public final class ProtocoloClienteServidorJSON implements IClienteServidor {

    private final ISerializador json;
    private final ValidadorPeticiones validador;

    public ProtocoloClienteServidorJSON(ISerializador json, ValidadorPeticiones validador) {
        this.json = Objects.requireNonNull(json, "json");
        this.validador = Objects.requireNonNull(validador, "validador");
    }

    @Override
    public PeticionDTO interpretar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new PeticionInvalidaException(null, CodigoError.PETICION_INVALIDA, "Petición vacía");
        }
        JsonNode arbol;
        try {
            arbol = json.desdeJson(texto, JsonNode.class);
        } catch (IllegalArgumentException e) {
            throw new PeticionInvalidaException(null, CodigoError.PETICION_INVALIDA, "JSON mal formado");
        }
        if (arbol == null || !arbol.isObject()) {
            throw new PeticionInvalidaException(null, CodigoError.PETICION_INVALIDA, "La petición debe ser un objeto JSON");
        }
        JsonNode id = arbol.get("idPeticion");
        String idPeticion = id != null && id.isTextual() && !id.asText().isBlank() ? id.asText() : null;
        if (idPeticion == null) {
            throw new PeticionInvalidaException(null, CodigoError.PETICION_INVALIDA, "Falta idPeticion");
        }
        PeticionDTO peticion;
        try {
            peticion = json.convertir(arbol, PeticionDTO.class);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PeticionInvalidaException(idPeticion, CodigoError.PETICION_INVALIDA,
                    "Comando, parámetros o contenido con formato inválido");
        }
        validador.validar(peticion);
        return peticion;
    }

    @Override
    public String serializar(RespuestaDTO r) {
        Objects.requireNonNull(r, "r");
        ObjectNode arbol = (ObjectNode) json.aArbol(r);
        if (!arbol.has("idPeticion")) {
            arbol.putNull("idPeticion");
        }
        return json.aJson(arbol);
    }

    @Override
    public String serializar(EventoPushDTO e) {
        return json.aJson(Objects.requireNonNull(e, "e"));
    }

    @Override
    public RespuestaDTO exito(String idPeticion, Object datos) {
        return new RespuestaDTO(idPeticion, true, CodigoError.OK, "OK", datos == null ? null : json.aArbol(datos));
    }

    @Override
    public RespuestaDTO error(String idPeticion, CodigoError c, String detalle) {
        if (Objects.requireNonNull(c, "c") == CodigoError.OK) {
            throw new IllegalArgumentException("OK no es un código de error");
        }
        return new RespuestaDTO(idPeticion, false, c, detalle, null);
    }
}
