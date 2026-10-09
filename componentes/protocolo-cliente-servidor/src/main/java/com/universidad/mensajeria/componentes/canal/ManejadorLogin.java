package com.universidad.mensajeria.componentes.canal;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.utilerias.ISerializador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * LOGIN: parámetros {@code codigo} y {@code clave}. Si son válidos asocia el
 * usuario a la conexión y devuelve el {@link UsuarioDTO} en {@code datos}.
 */
public final class ManejadorLogin implements IManejadorComando {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorLogin.class);

    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final ISerializador json;

    public ManejadorLogin(IUsuariosDisponibles usuarios, IGestorConexiones conexiones, ISerializador json) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente origen) {
        if (origen.getUsuario().isPresent()) {
            return RespuestaDTO.error(p.idPeticion(), "La sesión ya está iniciada en esta conexión");
        }
        Optional<String> codigo = p.parametro("codigo");
        Optional<String> clave = p.parametro("clave");
        if (codigo.isEmpty() || clave.isEmpty()) {
            return RespuestaDTO.error(p.idPeticion(), "Faltan los parámetros codigo y clave");
        }
        Optional<UsuarioDTO> usuario = usuarios.autenticar(codigo.get(), clave.get());
        if (usuario.isEmpty()) {
            LOG.warn("Login fallido para {} desde {}", codigo.get(), origen.getIpRemota());
            return RespuestaDTO.error(p.idPeticion(), "Credenciales inválidas");
        }
        UsuarioDTO u = usuario.get();
        conexiones.asociarUsuario(origen.getId(), u);
        LOG.info("Login de {} en la conexión {} desde {}", u.codigo(), origen.getId(), origen.getIpRemota());
        JsonNode datos = json.desdeJson(json.aJson(u), JsonNode.class);
        return RespuestaDTO.ok(p.idPeticion(), "Bienvenido, " + u.nombres(), datos);
    }
}
