package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

/**
 * Componente "ProtocoloClienteServidor": exige sesión iniciada (salvo para
 * LOGIN) y delega cada comando en su manejador. Independiente del transporte.
 */
public final class ProtocoloClienteServidor implements IClienteServidor {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocoloClienteServidor.class);

    private final FabricaManejadoresComando fabrica;

    public ProtocoloClienteServidor(FabricaManejadoresComando fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    @Override
    public RespuestaDTO procesar(PeticionDTO p, ICanalCliente origen) {
        if (p.comando() != TipoComando.LOGIN && origen.getUsuario().isEmpty()) {
            return RespuestaDTO.error(p.idPeticion(), "Debe iniciar sesión");
        }
        try {
            return fabrica.obtener(p.comando()).ejecutar(p, origen);
        } catch (RuntimeException e) {
            LOG.error("Error atendiendo {} de la conexión {}", p.comando(), origen.getId(), e);
            return RespuestaDTO.error(p.idPeticion(), "Error interno del servidor");
        }
    }

    @Override
    public void notificar(EventoPushDTO e, Collection<ICanalCliente> destinos) {
        destinos.forEach(canal -> canal.push(e));
    }
}
