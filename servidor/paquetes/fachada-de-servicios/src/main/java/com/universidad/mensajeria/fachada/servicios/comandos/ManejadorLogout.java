package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ControlSesiones;

import java.util.Objects;

/**
 * LOGOUT: cierra la sesión y responde. El canal lo cierra el receptor
 * después de enviar la respuesta.
 */
public final class ManejadorLogout implements IManejadorComando {

    private final ControlSesiones sesiones;
    private final IClienteServidor clienteServidor;

    public ManejadorLogout(ControlSesiones sesiones, IClienteServidor clienteServidor) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        sesiones.cerrar(c.getId(), "LOGOUT");
        return clienteServidor.exito(p.idPeticion(), null);
    }
}
