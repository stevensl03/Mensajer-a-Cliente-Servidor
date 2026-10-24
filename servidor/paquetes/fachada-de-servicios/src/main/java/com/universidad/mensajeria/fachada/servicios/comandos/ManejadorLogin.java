package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ControlSesiones;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * LOGIN {codigo, clave}: autentica y abre la sesión respetando el cupo global.
 * Responde {usuario, idSesion, ipServidor}, con {@code idSesion == canal.getId()}.
 */
public final class ManejadorLogin implements IManejadorComando {

    private final ControlSesiones sesiones;
    private final IClienteServidor clienteServidor;
    private final Supplier<String> ipServidor;

    /**
     * @param ipServidor IP de escucha del servidor (la da el transporte).
     */
    public ManejadorLogin(ControlSesiones sesiones, IClienteServidor clienteServidor, Supplier<String> ipServidor) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.ipServidor = Objects.requireNonNull(ipServidor, "ipServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        String codigo = IManejadorComando.requerido(p, "codigo");
        String clave = IManejadorComando.requerido(p, "clave");
        UsuarioDTO u = sesiones.abrir(c, codigo, clave);
        return clienteServidor.exito(p.idPeticion(),
                Map.of("usuario", u, "idSesion", c.getId(), "ipServidor", ipServidor.get()));
    }
}
