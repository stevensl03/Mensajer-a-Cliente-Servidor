package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.utilerias.ISerializador;
import com.universidad.mensajeria.utilerias.SerializadorJSON;

/** IClienteServidor de prueba: JSON con SerializadorJSON y sin validación. */
final class ClienteServidorFalso implements IClienteServidor {

    static final ISerializador JSON = new SerializadorJSON();

    @Override
    public PeticionDTO interpretar(String json) {
        return JSON.desdeJson(json, PeticionDTO.class);
    }

    @Override
    public String serializar(RespuestaDTO r) {
        return JSON.aJson(r);
    }

    @Override
    public String serializar(EventoPushDTO e) {
        return JSON.aJson(e);
    }

    @Override
    public RespuestaDTO exito(String idPeticion, Object datos) {
        return new RespuestaDTO(idPeticion, true, CodigoError.OK, "OK", datos == null ? null : JSON.aArbol(datos));
    }

    @Override
    public RespuestaDTO error(String idPeticion, CodigoError c, String detalle) {
        return new RespuestaDTO(idPeticion, false, c, detalle, null);
    }
}
