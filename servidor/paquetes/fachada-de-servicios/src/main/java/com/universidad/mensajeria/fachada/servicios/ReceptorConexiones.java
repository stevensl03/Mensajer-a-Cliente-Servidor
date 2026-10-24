package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.canal.PeticionInvalidaException;
import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.comandos.DespachadorComandos;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.ILogEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Recibe los eventos del transporte (§6.2 y §6.6): interpreta el JSON con
 * IClienteServidor, despacha el comando y responde. Con CUPO_LLENO o tras un
 * LOGOUT correcto, cierra el canal después de responder.
 */
public final class ReceptorConexiones implements IReceptorConexiones {

    private static final Logger LOG = LoggerFactory.getLogger(ReceptorConexiones.class);

    private final IClienteServidor clienteServidor;
    private final DespachadorComandos despachador;
    private final ControlSesiones sesiones;
    private final IGestorConexiones conexiones;
    private final ILogEventos log;
    private final EnrutadorMensajes enrutador;
    private final Supplier<String> ipServidor;
    private final int puerto;
    private final String protocolo;
    private volatile String motivoDesconexion = "DESCONEXION";

    /**
     * @param ipServidor IP de escucha (la da el transporte).
     * @param puerto     puerto configurado.
     * @param protocolo  protocolo configurado (TCP).
     */
    public ReceptorConexiones(IClienteServidor clienteServidor, DespachadorComandos despachador,
                              ControlSesiones sesiones, IGestorConexiones conexiones, ILogEventos log,
                              EnrutadorMensajes enrutador, Supplier<String> ipServidor, int puerto,
                              String protocolo) {
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.despachador = Objects.requireNonNull(despachador, "despachador");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.log = Objects.requireNonNull(log, "log");
        this.enrutador = Objects.requireNonNull(enrutador, "enrutador");
        this.ipServidor = Objects.requireNonNull(ipServidor, "ipServidor");
        this.puerto = puerto;
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
    }

    /** La conexión sola no ocupa cupo: se registra y se le envía INFO_SERVIDOR. */
    @Override
    public void alConectar(ICanalCliente c) {
        log.registrar(TipoEvento.CONEXION, "CONEXION canal=" + c.getId() + " ip=" + c.getIpRemota(),
                null, c.getIpRemota());
        enrutador.enviarACanal(c, enrutador.evento(TipoEventoPush.INFO_SERVIDOR, Map.of(
                "ipServidor", ipServidor.get(),
                "puerto", puerto,
                "protocolo", protocolo,
                "cuposLibres", conexiones.cuposLibres())));
    }

    @Override
    public void alRecibir(ICanalCliente c, String json) {
        RespuestaDTO r;
        PeticionDTO p = null;
        try {
            p = clienteServidor.interpretar(json);
            r = despachador.despachar(p, c);
        } catch (PeticionInvalidaException e) {
            r = clienteServidor.error(e.getIdPeticion(), e.getCodigo(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Error al atender una trama del canal {}", c.getId(), e);
            r = clienteServidor.error(p == null ? null : p.idPeticion(), CodigoError.ERROR_INTERNO,
                    "Error interno del servidor");
        }
        enviar(c, clienteServidor.serializar(r));
        boolean logoutCorrecto = p != null && p.comando() == TipoComando.LOGOUT && r.exito();
        if (r.codigo() == CodigoError.CUPO_LLENO || logoutCorrecto) {
            c.cerrar();
        }
    }

    @Override
    public void alDesconectar(ICanalCliente c) {
        sesiones.cerrar(c.getId(), motivoDesconexion);
    }

    /** Desde el apagado ordenado, las desconexiones se registran como SERVIDOR_DETENIDO. */
    void servidorDetenido() {
        motivoDesconexion = "SERVIDOR_DETENIDO";
    }

    private static void enviar(ICanalCliente c, String trama) {
        try {
            c.enviar(trama);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo enviar al canal {}", c.getId(), e);
        }
    }
}
