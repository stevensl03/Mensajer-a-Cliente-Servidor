package com.universidad.mensajeria.componentes.protocolo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Base de los servidores (Template Method): cada transporte implementa
 * {@link #iniciar(int)} y {@link #detener()} y reutiliza aquí el ciclo de vida
 * común de una conexión: cupo al conectar, atención de peticiones y limpieza
 * al desconectar.
 */
public abstract class ServidorBase implements IProtocoloComunicacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServidorBase.class);

    protected final IClienteServidor clienteServidor;
    protected final IGestorConexiones conexiones;
    protected final ICodificadorMensajes codec;

    protected ServidorBase(IClienteServidor clienteServidor, IGestorConexiones conexiones,
                           ICodificadorMensajes codec) {
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Aplica el cupo global a una conexión nueva. Si no hay cupo, envía
     * {@code CUPO_LLENO} y cierra el canal.
     *
     * @return {@code true} si la conexión quedó registrada.
     */
    protected boolean alConectar(ICanalCliente canal) {
        if (conexiones.registrar(canal)) {
            LOG.info("Conexión {} abierta desde {}", canal.getId(), canal.getIpRemota());
            return true;
        }
        LOG.warn("Cupo de sesiones lleno: se rechaza la conexión desde {}", canal.getIpRemota());
        var datos = JsonNodeFactory.instance.objectNode()
                .put("mensaje", "Se alcanzó el número máximo de sesiones. Intente más tarde.");
        canal.push(EventoPushDTO.de(TipoEventoPush.CUPO_LLENO, datos));
        canal.cerrar();
        return false;
    }

    /** Atiende una petición y encola su respuesta. Un fallo nunca tumba la conexión. */
    protected void atender(PeticionDTO p, ICanalCliente canal) {
        RespuestaDTO respuesta;
        try {
            respuesta = clienteServidor.procesar(p, canal);
        } catch (RuntimeException e) {
            LOG.error("Error atendiendo {} de la conexión {}", p.comando(), canal.getId(), e);
            respuesta = RespuestaDTO.error(p.idPeticion(), "Error interno del servidor");
        }
        canal.enviar(respuesta);
    }

    /** Responde a un mensaje que no se pudo leer como petición. */
    protected void atenderInvalida(ICanalCliente canal, String motivo) {
        LOG.debug("Petición inválida en la conexión {}: {}", canal.getId(), motivo);
        canal.enviar(RespuestaDTO.error(null, "Petición inválida: " + motivo));
    }

    /** Libera el cupo y cierra el canal. Idempotente. */
    protected void alDesconectar(ICanalCliente canal) {
        conexiones.cerrar(canal.getId());
        canal.cerrar();
        LOG.info("Conexión {} cerrada{}", canal.getId(),
                canal.getUsuario().map(u -> " (usuario " + u.codigo() + ")").orElse(""));
    }

    @Override
    public String getIpServidor() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }
}
