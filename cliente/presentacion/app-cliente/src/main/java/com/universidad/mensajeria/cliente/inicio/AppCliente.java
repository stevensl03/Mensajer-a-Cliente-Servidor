package com.universidad.mensajeria.cliente.inicio;

import com.universidad.mensajeria.cliente.comunicacion.FabricaConexiones;
import com.universidad.mensajeria.cliente.comunicacion.IConexionServidor;
import com.universidad.mensajeria.cliente.comunicacion.TipoProtocolo;
import com.universidad.mensajeria.cliente.fachada.FachadaCliente;
import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.persistencia.FabricaRepositoriosH2;
import com.universidad.mensajeria.cliente.persistencia.ProveedorConexionH2;
import com.universidad.mensajeria.cliente.protocolo.FabricaProtocoloCliente;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.servicios.chat.CacheMensajesSesion;
import com.universidad.mensajeria.cliente.servicios.chat.ServicioChat;
import com.universidad.mensajeria.cliente.servicios.informes.ServicioInformes;
import com.universidad.mensajeria.cliente.servicios.usuarios.ServicioUsuarios;
import com.universidad.mensajeria.cliente.utilerias.ConfiguracionCliente;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Composition Root del cliente (cliente §5.1): el único punto que conoce las
 * clases concretas. Crea todo con fábricas e inyecta {@link IFachadaCliente}
 * en los ViewModels a través de {@link FabricaVistas}.
 *
 * <p>Se ejecuta desde {@code cliente/}: las rutas {@code config/} y
 * {@code data/} son relativas a ese directorio.</p>
 */
public final class AppCliente extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(AppCliente.class);
    static final Path CONFIG = Path.of("config/cliente.properties");

    private IFachadaCliente fachada;
    private ExecutorService fondo;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((h, e) -> LOG.error("Error no controlado en {}", h.getName(), e));
        ConfiguracionCliente cfg;
        try {
            cfg = ConfiguracionCliente.cargar(CONFIG);
            fachada = ensamblar(cfg);
        } catch (RuntimeException e) {
            LOG.error("El cliente no pudo arrancar", e);
            error("No se pudo iniciar el cliente", e.getMessage() + "\n\nArchivo: " + CONFIG.toAbsolutePath());
            Platform.exit();
            return;
        }
        // Hilos virtuales: no impiden que la JVM termine al cerrar la ventana.
        fondo = Executors.newVirtualThreadPerTaskExecutor();
        var navegador = new AtomicReference<NavegadorVistas>();
        var fabrica = new FabricaVistas(fachada, Platform::runLater, fondo, cfg.host() + ":" + cfg.puerto(),
                t -> navegador.get().ir(t));
        registrarVistas(fabrica);
        navegador.set(new NavegadorVistas(stage, fabrica));
        try {
            navegador.get().ir(TipoVista.LOGIN);
        } catch (RuntimeException e) {
            LOG.error("No se pudo mostrar la pantalla de inicio", e);
            error("No se pudo mostrar la pantalla de inicio", e.getMessage());
            Platform.exit();
        }
    }

    /** Cierra la sesión (espera acotada, R-14), la conexión y la base local. Idempotente. */
    @Override
    public void stop() {
        if (fachada != null) {
            try {
                fachada.logout();
            } catch (RuntimeException e) {
                LOG.warn("Fallo cerrando la sesión al salir: {}", e.getMessage());
            }
        }
        if (fondo != null) {
            fondo.shutdownNow();
        }
    }

    /**
     * Ensamblado exacto de cliente §5.1 (con D-03 y D-04). Visible en el paquete
     * para probar la pila completa sin JavaFX.
     *
     * @param cfg configuración cargada.
     * @return la fachada, ya suscrita a los eventos del protocolo.
     */
    static IFachadaCliente ensamblar(ConfiguracionCliente cfg) {
        var json = new SerializadorJSON();
        IConexionServidor conexion = new FabricaConexiones()
                .crear(TipoProtocolo.valueOf(cfg.protocolo()), cfg.maxTramaBytes());
        IProtocoloCliente protocolo = new FabricaProtocoloCliente()
                .crear(conexion, json, Duration.ofSeconds(cfg.timeoutSegundos()));
        var proveedor = new ProveedorConexionH2(cfg.urlH2(), cfg.usuarioH2(), cfg.claveH2());
        var repos = new FabricaRepositoriosH2(proveedor);
        var usuarios = new ServicioUsuarios(protocolo, proveedor, repos.sesiones(), json);
        var chat = new ServicioChat(protocolo, repos.mensajes(), new CacheMensajesSesion(cfg.cacheMax()), json);
        var informes = new ServicioInformes(protocolo, json);
        var fachada = new FachadaCliente(usuarios, chat, informes, protocolo, json);
        protocolo.suscribir(fachada);
        return fachada;
    }

    /** Registra el controlador de cada pantalla con su ViewModel (fases 11b–11d). */
    private static void registrarVistas(FabricaVistas fabrica) {
        // Las pantallas C1–C3 se registran aquí a medida que se construyen.
    }

    private static void error(String titulo, String detalle) {
        var alerta = new Alert(Alert.AlertType.ERROR, detalle);
        alerta.setTitle("Mensajería Académica");
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }
}
