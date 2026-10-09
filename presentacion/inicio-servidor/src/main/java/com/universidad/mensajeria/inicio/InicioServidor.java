package com.universidad.mensajeria.inicio;

import com.universidad.mensajeria.fachada.IFachadaDeServicios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Punto de entrada del servidor.
 *
 * <p>Por ahora arranca el servidor sin vista y espera hasta que se detenga el
 * proceso (Ctrl+C). La vista de consola o escritorio llega en su fase.</p>
 */
public class InicioServidor {

    private static final Logger LOG = LoggerFactory.getLogger(InicioServidor.class);

    /**
     * @param args opcional: directorio de configuración (por defecto {@code config}).
     * @throws InterruptedException si se interrumpe la espera.
     */
    public static void main(String[] args) throws InterruptedException {
        Path dirConfig = Path.of(args.length > 0 ? args[0] : "config");
        ComponenteFactory fabrica = ComponenteFactory.desdeDirectorio(dirConfig);
        fabrica.importarUsuarios();
        IFachadaDeServicios fachada = fabrica.crearFachada();
        fachada.iniciar();

        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("apagado").unstarted(() -> {
            LOG.info("Deteniendo el servidor...");
            fachada.detener();
        }));
        LOG.info("Servidor iniciado. Ctrl+C para detenerlo.");
        new CountDownLatch(1).await();
    }

    private InicioServidor() {
    }
}
