package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;

import java.util.Objects;

/**
 * ENVIAR_PRIVADO {destino, contenido}: el destino debe existir (puede ser el
 * ADMIN) y ser distinto del remitente; si no, DESTINO_INEXISTENTE.
 */
public final class ManejadorEnviarPrivado implements IManejadorComando {

    private final IGestorMensajes gestor;
    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorEnviarPrivado(IGestorMensajes gestor, IUsuariosDisponibles usuarios,
                                  IGestorConexiones conexiones, IClienteServidor clienteServidor) {
        this.gestor = Objects.requireNonNull(gestor, "gestor");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        String destino = IManejadorComando.requerido(p, "destino");
        boolean aSiMismo = conexiones.usuarioDe(c.getId()).map(u -> u.codigo().equals(destino)).orElse(false);
        if (!usuarios.existe(destino) || aSiMismo) {
            throw new ExcepcionNegocio(CodigoError.DESTINO_INEXISTENTE, "El destinatario no existe: " + destino);
        }
        return ManejadorEnviarForo.encolar(p, c, Canal.PRIVADO, destino, gestor, conexiones, clienteServidor);
    }
}
