package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.componentes.canal.FabricaManejadoresComando;
import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.canal.ProtocoloClienteServidor;
import com.universidad.mensajeria.componentes.gestor.GestorColasDeMensajes;
import com.universidad.mensajeria.componentes.gestor.InterfazGestorMensajes;
import com.universidad.mensajeria.componentes.protocolo.FabricaProtocolos;
import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.servicios.FabricaFiltros;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.componentes.servicios.ProcesadorImagen;
import com.universidad.mensajeria.componentes.servicios.ProcesadorTexto;
import com.universidad.mensajeria.componentes.servicios.ServiciosProcesamiento;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.componentes.usuarios.ImportadorUsuarios;
import com.universidad.mensajeria.componentes.usuarios.ServicioUsuarios;
import com.universidad.mensajeria.fachada.IFachadaDeServicios;
import com.universidad.mensajeria.fachada.servicios.FachadaDeServicios;
import com.universidad.mensajeria.persistencia.IPersistencia;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;
import com.universidad.mensajeria.persistencia.Persistencia;
import com.universidad.mensajeria.persistencia.RepositorioUsuariosMemoria;
import com.universidad.mensajeria.soporte.almacenar.AlmacenarInformacion;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.LogDeEventos;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.ISerializador;
import com.universidad.mensajeria.utilerias.SerializadorJSON;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Fábrica de componentes del servidor: único lugar donde se instancian clases
 * concretas. En una fase posterior se convierte en {@code ServidorApp}
 * (Composition Root del diagrama).
 */
public class ComponenteFactory {

    private final ConfiguracionServidor configuracion;
    private final IGeneradorHash hash = new GeneradorHashSHA256();
    private final ISerializador json = new SerializadorJSON();
    /** Transitorio hasta tener RepositorioUsuariosJDBC (fase de persistencia). */
    private final IRepositorioUsuarios repositorioUsuarios = new RepositorioUsuariosMemoria();

    /**
     * @param configuracion configuración del servidor.
     */
    public ComponenteFactory(ConfiguracionServidor configuracion) {
        this.configuracion = Objects.requireNonNull(configuracion, "configuracion");
    }

    /**
     * Carga la configuración desde un directorio.
     *
     * @param dirConfig directorio con {@code servidor.properties} y {@code bd.properties}.
     * @return la fábrica lista para construir componentes.
     */
    public static ComponenteFactory desdeDirectorio(Path dirConfig) {
        return new ComponenteFactory(ConfiguracionServidor.cargar(dirConfig));
    }

    /** Carga inicial de usuarios desde el archivo plano, si el repositorio está vacío. */
    public void importarUsuarios() {
        new ImportadorUsuarios(configuracion.getArchivoUsuarios(), repositorioUsuarios, hash).importarSiVacio();
    }

    /** @return la implementación de persistencia configurada. */
    public IPersistencia crearPersistencia() {
        return new Persistencia(configuracion.getUrlBD(), configuracion.getUsuarioBD(), configuracion.getClaveBD());
    }

    /** @return el componente de procesamiento de texto e imágenes. */
    public IServiciosDisponibles crearServicios() {
        var filtros = new FabricaFiltros(hash);
        return new ServiciosProcesamiento(
                new ProcesadorTexto(hash),
                new ProcesadorImagen(hash, filtros.crearPipelinePorDefecto()));
    }

    /** @return el componente de usuarios. */
    public IUsuariosDisponibles crearUsuarios() {
        return new ServicioUsuarios(repositorioUsuarios, hash);
    }

    /** @return la implementación del gestor de mensajes configurada. */
    public InterfazGestorMensajes crearGestor() {
        return new GestorColasDeMensajes();
    }

    /**
     * Construye la fachada con todos sus componentes inyectados.
     *
     * @return la fachada configurada.
     */
    public IFachadaDeServicios crearFachada() {
        IUsuariosDisponibles usuarios = crearUsuarios();
        IGestorConexiones conexiones = new GestorConexiones(configuracion.getMaxSesiones());
        IClienteServidor clienteServidor = new ProtocoloClienteServidor(
                new FabricaManejadoresComando(usuarios, conexiones, json));
        IProtocoloComunicacion protocolo = new FabricaProtocolos(json, configuracion.getMaxTramaBytes())
                .crear(configuracion.getProtocolo(), clienteServidor, conexiones);

        AlmacenarInformacion almacenar = new AlmacenarInformacion(crearPersistencia());
        return new FachadaDeServicios(
                protocolo,
                usuarios,
                crearServicios(),
                crearGestor(),
                conexiones,
                new LogDeEventos(almacenar),
                almacenar,
                configuracion.getPuerto());
    }
}
