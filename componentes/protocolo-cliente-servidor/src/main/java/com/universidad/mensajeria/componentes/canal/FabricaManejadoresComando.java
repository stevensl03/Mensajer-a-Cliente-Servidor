package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Asocia cada {@link TipoComando} con su manejador. Los comandos de fases
 * futuras responden "Comando aún no disponible".
 */
public final class FabricaManejadoresComando {

    private static final IManejadorComando NO_DISPONIBLE = (p, origen) ->
            RespuestaDTO.error(p.idPeticion(), "Comando aún no disponible: " + p.comando());

    private final Map<TipoComando, IManejadorComando> manejadores = new EnumMap<>(TipoComando.class);

    public FabricaManejadoresComando(IUsuariosDisponibles usuarios, IGestorConexiones conexiones,
                                     ISerializador json) {
        manejadores.put(TipoComando.LOGIN, new ManejadorLogin(usuarios, conexiones, json));
        manejadores.put(TipoComando.LOGOUT, new ManejadorLogout(conexiones));
    }

    /**
     * @param c comando.
     * @return su manejador, o uno que responde "no disponible".
     */
    public IManejadorComando obtener(TipoComando c) {
        return manejadores.getOrDefault(Objects.requireNonNull(c, "c"), NO_DISPONIBLE);
    }
}
