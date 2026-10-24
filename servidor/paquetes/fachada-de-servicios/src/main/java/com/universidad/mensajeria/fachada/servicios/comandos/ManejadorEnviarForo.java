package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ENVIAR_FORO {contenido}: encola el mensaje y responde de inmediato
 * {idMensaje, estado: ENCOLADO}. El remitente sale de la sesión.
 */
public final class ManejadorEnviarForo implements IManejadorComando {

    private final IGestorMensajes gestor;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorEnviarForo(IGestorMensajes gestor, IGestorConexiones conexiones,
                               IClienteServidor clienteServidor) {
        this.gestor = Objects.requireNonNull(gestor, "gestor");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        return encolar(p, c, Canal.FORO, null, gestor, conexiones, clienteServidor);
    }

    /** Arma la solicitud (§6.3, pasos 3 y 4); la comparten FORO y PRIVADO. */
    static RespuestaDTO encolar(PeticionDTO p, ICanalCliente c, Canal canal, String destino,
                                IGestorMensajes gestor, IGestorConexiones conexiones,
                                IClienteServidor clienteServidor) {
        if (p.contenido() == null) {
            throw new ExcepcionNegocio(CodigoError.CONTENIDO_INVALIDO, "El mensaje no tiene contenido");
        }
        String remitente = conexiones.usuarioDe(c.getId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO, "Primero debe iniciar sesión"))
                .codigo();
        var s = new SolicitudMensajeDTO(UUID.randomUUID().toString(), p.idPeticion(), c.getId(), remitente,
                canal, destino, p.contenido(), LocalDateTime.now());
        if (!gestor.encolar(s)) {
            throw new ExcepcionNegocio(CodigoError.COLA_LLENA, "El servidor está ocupado; intente de nuevo");
        }
        return clienteServidor.exito(p.idPeticion(), Map.of("idMensaje", s.idMensaje(), "estado", "ENCOLADO"));
    }
}
