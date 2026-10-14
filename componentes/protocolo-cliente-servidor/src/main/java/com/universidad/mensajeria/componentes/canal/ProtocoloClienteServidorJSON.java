package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.Objects;

/**
 * Implementación JSON (Jackson) de {@link IClienteServidor}. No conoce
 * sockets ni casos de uso.
 */
public final class ProtocoloClienteServidorJSON implements IClienteServidor {

    private final ISerializador json;
    private final ValidadorPeticiones validador;

    public ProtocoloClienteServidorJSON(ISerializador json, ValidadorPeticiones validador) {
        this.json = Objects.requireNonNull(json, "json");
        this.validador = Objects.requireNonNull(validador, "validador");
    }

    @Override
    public PeticionDTO interpretar(String json) {
        throw new UnsupportedOperationException("Fase 4d");
    }

    @Override
    public String serializar(RespuestaDTO r) {
        throw new UnsupportedOperationException("Fase 4d");
    }

    @Override
    public String serializar(EventoPushDTO e) {
        throw new UnsupportedOperationException("Fase 4d");
    }

    @Override
    public RespuestaDTO exito(String idPeticion, Object datos) {
        throw new UnsupportedOperationException("Fase 4d");
    }

    @Override
    public RespuestaDTO error(String idPeticion, CodigoError c, String detalle) {
        throw new UnsupportedOperationException("Fase 4d");
    }
}
