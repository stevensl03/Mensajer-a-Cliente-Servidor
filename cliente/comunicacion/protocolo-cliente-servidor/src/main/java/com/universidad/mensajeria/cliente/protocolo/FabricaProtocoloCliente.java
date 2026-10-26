package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.cliente.comunicacion.IConexionServidor;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;

import java.time.Duration;

/** Crea el protocolo JSON sobre un transporte ya creado. */
public final class FabricaProtocoloCliente {

    /**
     * @param c       transporte (sin abrir).
     * @param json    serializador.
     * @param timeout espera máxima de una respuesta.
     * @return el protocolo, ya registrado como receptor al conectar.
     */
    public IProtocoloCliente crear(IConexionServidor c, ISerializador json, Duration timeout) {
        return new ClienteProtocoloJSON(c, json, timeout);
    }
}
