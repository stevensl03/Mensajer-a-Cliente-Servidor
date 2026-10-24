package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LISTAR_USUARIOS: todos los registrados con {@code conectado} y
 * {@code sesiones}. El ADMIN aparece siempre conectado.
 */
public final class ManejadorListarUsuarios implements IManejadorComando {

    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorListarUsuarios(IUsuariosDisponibles usuarios, IGestorConexiones conexiones,
                                   IClienteServidor clienteServidor) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        return clienteServidor.exito(p.idPeticion(), Map.of("usuarios", estados()));
    }

    /** @return el estado de cada usuario registrado (lo usa también la fachada). */
    public List<EstadoUsuarioDTO> estados() {
        return usuarios.listarRegistrados().stream()
                .map(u -> {
                    int sesiones = conexiones.canalesDe(u.codigo()).size();
                    return new EstadoUsuarioDTO(u, u.rol() == Rol.ADMIN || sesiones > 0, sesiones);
                })
                .toList();
    }
}
