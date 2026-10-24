package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.IGestorMensajes;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.fachada.servicios.ControlSesiones;
import com.universidad.mensajeria.fachada.servicios.GeneradorInformes;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registra un manejador por cada {@link TipoComando} (Factory).
 */
public final class FabricaManejadoresComando {

    private final ControlSesiones sesiones;
    private final GeneradorInformes informes;
    private final IGestorMensajes gestor;
    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final IAlmacenMensajes mensajes;
    private final IClienteServidor clienteServidor;
    private final Supplier<String> ipServidor;

    public FabricaManejadoresComando(ControlSesiones sesiones, GeneradorInformes informes, IGestorMensajes gestor,
                                     IUsuariosDisponibles usuarios, IGestorConexiones conexiones,
                                     IAlmacenMensajes mensajes, IClienteServidor clienteServidor,
                                     Supplier<String> ipServidor) {
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.informes = Objects.requireNonNull(informes, "informes");
        this.gestor = Objects.requireNonNull(gestor, "gestor");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
        this.ipServidor = Objects.requireNonNull(ipServidor, "ipServidor");
    }

    /**
     * @return un manejador por cada comando del protocolo.
     * @throws IllegalStateException si a algún comando le falta su manejador.
     */
    public EnumMap<TipoComando, IManejadorComando> crearTodos() {
        var m = new EnumMap<TipoComando, IManejadorComando>(TipoComando.class);
        m.put(TipoComando.LOGIN, new ManejadorLogin(sesiones, clienteServidor, ipServidor));
        m.put(TipoComando.LOGOUT, new ManejadorLogout(sesiones, clienteServidor));
        m.put(TipoComando.LISTAR_USUARIOS, new ManejadorListarUsuarios(usuarios, conexiones, clienteServidor));
        m.put(TipoComando.ENVIAR_FORO, new ManejadorEnviarForo(gestor, conexiones, clienteServidor));
        m.put(TipoComando.ENVIAR_PRIVADO, new ManejadorEnviarPrivado(gestor, usuarios, conexiones, clienteServidor));
        m.put(TipoComando.HISTORIAL, new ManejadorHistorial(mensajes, usuarios, conexiones, clienteServidor));
        m.put(TipoComando.INFORME, new ManejadorInforme(informes, conexiones, clienteServidor));
        m.put(TipoComando.OBTENER_IMAGEN, new ManejadorObtenerImagen(mensajes, conexiones, clienteServidor));
        Set<TipoComando> faltantes = EnumSet.complementOf(EnumSet.copyOf(m.keySet()));
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Comandos sin manejador: " + faltantes);
        }
        return m;
    }
}
