package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * LOGOUT: confirma al cliente y cierra la conexión.
 *
 * <p>La respuesta se encola aquí, antes de cerrar, para que el canal la
 * entregue antes del cierre. La que devuelve este método la encola el
 * transporte después y el canal ya cerrado la descarta.</p>
 */
public final class ManejadorLogout implements IManejadorComando {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorLogout.class);

    private final IGestorConexiones conexiones;

    public ManejadorLogout(IGestorConexiones conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente origen) {
        RespuestaDTO respuesta = RespuestaDTO.ok(p.idPeticion(), "Sesión cerrada", null);
        origen.enviar(respuesta);
        conexiones.cerrar(origen.getId());
        LOG.info("Logout de {} en la conexión {}",
                origen.getUsuario().map(u -> u.codigo()).orElse("?"), origen.getId());
        return respuesta;
    }
}
