package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.componentes.canal.FabricaClienteServidor;
import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.gestor.GestorColasDeMensajes;
import com.universidad.mensajeria.componentes.protocolo.FabricaProtocolos;
import com.universidad.mensajeria.componentes.protocolo.IProtocoloComunicacion;
import com.universidad.mensajeria.componentes.protocolo.TipoProtocolo;
import com.universidad.mensajeria.componentes.servicios.FabricaServicios;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.componentes.usuarios.FabricaUsuarios;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.servicios.FachadaServidor;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import com.universidad.mensajeria.persistencia.jdbc.DialectoMySQL;
import com.universidad.mensajeria.persistencia.jdbc.DialectoPostgreSQL;
import com.universidad.mensajeria.persistencia.jdbc.FabricaRepositoriosJDBC;
import com.universidad.mensajeria.persistencia.jdbc.ProveedorConexionJDBC;
import com.universidad.mensajeria.soporte.almacenar.AlmacenarInformacion;
import com.universidad.mensajeria.soporte.almacenar.ImportadorUsuarios;
import com.universidad.mensajeria.soporte.almacenar.cache.FabricaCaches;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.log.LogEventos;
import com.universidad.mensajeria.soporte.mensajes.AlmacenMensajesMemoria;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import com.universidad.mensajeria.utilerias.ISerializador;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Composition Root: único punto que conoce clases concretas. Carga la
 * configuración, ensambla el sistema (§6.1), elige la vista y registra el
 * apagado ordenado. Se ejecuta desde {@code servidor/}, donde están
 * {@code config/}, {@code almacen/} y {@code logs/}.
 *
 * <p>Uso: {@code java -jar inicio-servidor.jar [--vista=CONSOLA|ESCRITORIO]}.</p>
 */
public final class ServidorApp {

    private static final Logger LOG = LoggerFactory.getLogger(ServidorApp.class);

    /** Recursos que se cierran al final del apagado, en orden inverso al de creación. */
    private static final Deque<Runnable> CIERRES = new ArrayDeque<>();
    private static final AtomicBoolean APAGADO = new AtomicBoolean();

    private ServidorApp() {
    }

    public static void main(String[] args) {
        IFachadaServidor fachada;
        IVista vista;
        TipoVista tipo;
        try {
            // Paso 1: si falta una clave, el arranque falla con un mensaje claro.
            ConfiguracionServidor cfg = ConfiguracionServidor.cargar(
                    Path.of("config/servidor.properties"), Path.of("config/bd.properties"));
            tipo = tipoVista(args, cfg.vista());
            ISerializador json = new SerializadorJSON();
            fachada = ensamblar(cfg, json);
            vista = FabricaVistas.crear(tipo, fachada, json);
            fachada.iniciarServidor();
        } catch (RuntimeException e) {
            LOG.error("El servidor no pudo arrancar: {} (causa: {})", primeraLinea(e), primeraLinea(causaRaiz(e)));
            LOG.debug("Detalle del fallo de arranque", e);
            cerrarRecursos();
            System.exit(1);
            return;
        }
        registrarApagado(fachada, vista);
        LOG.info("Servidor listo en {}", fachada.getIpServidor());
        vista.iniciar();
        if (tipo == TipoVista.CONSOLA) {
            // El menú terminó (opción «0»): los hilos de la cola y del transporte no son daemon.
            System.exit(0);
        }
    }

    /**
     * Pasos 2 a 15 de §6.1. Los recursos que hay que cerrar al apagar (pool de
     * BD y Redis) quedan registrados en {@link #CIERRES}.
     */
    private static IFachadaServidor ensamblar(ConfiguracionServidor cfg, ISerializador json) {
        IGeneradorHash hash = new GeneradorHashSHA256();

        IDialectoSQL dialecto = switch (cfg.bd().getProperty("bd.motor").trim().toUpperCase(Locale.ROOT)) {
            case "MYSQL" -> new DialectoMySQL();
            case "POSTGRESQL" -> new DialectoPostgreSQL();
            default -> throw new IllegalStateException(
                    "bd.motor debe ser MYSQL o POSTGRESQL: " + cfg.bd().getProperty("bd.motor"));
        };
        var proveedor = new ProveedorConexionJDBC(cfg.bd(), dialecto);
        CIERRES.push(proveedor::cerrar);
        proveedor.migrar();

        var repos = new FabricaRepositoriosJDBC(proveedor, dialecto, cfg.directorioArchivos());
        var caches = new FabricaCaches(cfg.cache(), json);
        CIERRES.push(caches::cerrar);

        var almacen = new AlmacenarInformacion(repos, repos.archivos(),
                new ImportadorUsuarios(cfg.archivoUsuarios(), hash),
                caches.crear("mensajes", MensajeDTO.class),
                caches.crear("imagenes", byte[].class));
        almacen.inicializar();

        // Paso 7: aquí se entrega la lista, así Usuarios no conoce la persistencia.
        IUsuariosDisponibles usuarios = FabricaUsuarios.crear(almacen.usuariosRegistrados(), hash);
        IServiciosDisponibles servicios = FabricaServicios.crear(hash);
        var mensajes = new AlmacenMensajesMemoria(almacen);
        mensajes.cargarHistorial();
        var log = new LogEventos(almacen);
        log.cargarHistorial();
        var conexiones = new GestorConexiones(cfg.maxSesiones());
        var gestor = new GestorColasDeMensajes(cfg.colaCapacidad());
        IClienteServidor clienteServidor = FabricaClienteServidor.crear(json, cfg);
        IProtocoloComunicacion protocolo = FabricaProtocolos.crear(TipoProtocolo.valueOf(cfg.protocolo()), cfg);

        return new FachadaServidor(protocolo, clienteServidor, servicios, usuarios, gestor, mensajes,
                new FabricaMensajes(), log, conexiones, json, cfg);
    }

    /**
     * El hook cubre Ctrl+C, el cierre de la ventana y el fin de {@code main}
     * tras la opción «0» de la consola: los tres pasan por {@link System#exit}.
     */
    private static void registrarApagado(IFachadaServidor f, IVista v) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> apagar(f, v), "apagado"));
    }

    /** Apagado ordenado e idempotente: vista, fachada (§6.1) y por último BD y Redis. */
    static void apagar(IFachadaServidor f, IVista v) {
        if (!APAGADO.compareAndSet(false, true)) {
            return;
        }
        try {
            v.detener();
        } catch (RuntimeException e) {
            LOG.warn("La vista no se detuvo limpiamente", e);
        }
        try {
            f.detenerServidor();
        } catch (RuntimeException e) {
            LOG.warn("La fachada no se detuvo limpiamente", e);
        }
        cerrarRecursos();
        LOG.info("Servidor detenido");
    }

    private static Throwable causaRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz;
    }

    private static String primeraLinea(Throwable t) {
        String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        int salto = m.indexOf('\n');
        return salto < 0 ? m : m.substring(0, salto);
    }

    private static void cerrarRecursos() {
        while (!CIERRES.isEmpty()) {
            try {
                CIERRES.pop().run();
            } catch (RuntimeException e) {
                LOG.warn("No se pudo cerrar un recurso", e);
            }
        }
    }

    /**
     * @param args        argumentos de la línea de comandos.
     * @param porDefecto  valor de {@code servidor.vista}.
     * @return la vista pedida con {@code --vista=X} o {@code --vista X}, o la configurada.
     * @throws IllegalArgumentException si el valor no es CONSOLA ni ESCRITORIO.
     */
    static TipoVista tipoVista(String[] args, String porDefecto) {
        String valor = porDefecto;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--vista=")) {
                valor = args[i].substring("--vista=".length());
            } else if (args[i].equals("--vista") && i + 1 < args.length) {
                valor = args[++i];
            }
        }
        try {
            return TipoVista.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Vista desconocida: " + valor + " (use CONSOLA o ESCRITORIO)", e);
        }
    }
}
