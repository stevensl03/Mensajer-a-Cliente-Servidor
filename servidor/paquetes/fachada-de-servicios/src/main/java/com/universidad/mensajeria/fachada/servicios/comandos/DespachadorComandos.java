package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Busca el manejador del comando; exige sesión iniciada para todo comando
 * distinto de LOGIN.
 */
public final class DespachadorComandos {

    private final EnumMap<TipoComando, IManejadorComando> manejadores;
    private final IGestorConexiones conexiones;

    public DespachadorComandos(EnumMap<TipoComando, IManejadorComando> manejadores,
                               IGestorConexiones conexiones) {
        this.manejadores = new EnumMap<>(Objects.requireNonNull(manejadores, "manejadores"));
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    /**
     * @param p petición validada.
     * @param c canal del remitente.
     * @return la respuesta para el cliente.
     */
    public RespuestaDTO despachar(PeticionDTO p, ICanalCliente c) {
        throw new UnsupportedOperationException("Fase 6");
    }
}
