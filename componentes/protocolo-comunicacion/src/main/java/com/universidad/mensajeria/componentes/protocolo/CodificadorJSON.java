package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.utilerias.ISerializador;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Mensajes como JSON en UTF-8 (TCP y UDP). Cualquier lenguaje puede leerlos.
 */
public final class CodificadorJSON implements ICodificadorMensajes {

    private final ISerializador json;

    public CodificadorJSON(ISerializador json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public byte[] codificar(Object o) {
        return json.aJson(o).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T decodificar(byte[] b, Class<T> tipo) throws ProtocoloException {
        try {
            return json.desdeJson(new String(b, StandardCharsets.UTF_8), tipo);
        } catch (IllegalArgumentException e) {
            throw new ProtocoloException(e.getMessage(), e);
        }
    }
}
