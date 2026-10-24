package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Busca el manejador del comando; exige sesión iniciada para todo comando
 * distinto de LOGIN. Siempre devuelve una respuesta: los errores de negocio se
 * traducen a su código y los inesperados a ERROR_INTERNO.
 */
public final class DespachadorComandos {

    private static final Logger LOG = LoggerFactory.getLogger(DespachadorComandos.class);

    private final EnumMap<TipoComando, IManejadorComando> manejadores;
    private final IGestorConexiones conexiones;

    public DespachadorComandos(EnumMap<TipoComando, IManejadorComando> manejadores,
                               IGestorConexiones conexiones) {
        this.manejadores = new EnumMap<>(Objects.requireNonNull(manejadores, "manejadores"));
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    /**
     * @param p petición ya validada.
     * @param c canal que la envió.
     * @return la respuesta para el cliente.
     */
    public RespuestaDTO despachar(PeticionDTO p, ICanalCliente c) {
        IManejadorComando manejador = manejadores.get(p.comando());
        if (manejador == null) {
            return error(p, CodigoError.PETICION_INVALIDA, "Comando no soportado: " + p.comando());
        }
        if (p.comando() != TipoComando.LOGIN && conexiones.usuarioDe(c.getId()).isEmpty()) {
            return error(p, CodigoError.NO_AUTENTICADO, "Primero debe iniciar sesión");
        }
        try {
            return manejador.ejecutar(p, c);
        } catch (ExcepcionNegocio e) {
            return error(p, e.getCodigo(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Error al atender {} (petición {})", p.comando(), p.idPeticion(), e);
            return error(p, CodigoError.ERROR_INTERNO, "Error interno del servidor");
        }
    }

    private static RespuestaDTO error(PeticionDTO p, CodigoError codigo, String mensaje) {
        return new RespuestaDTO(p.idPeticion(), false, codigo, mensaje, null);
    }
}
