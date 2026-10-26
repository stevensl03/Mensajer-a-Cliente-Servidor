package com.universidad.mensajeria.cliente.inicio;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Carga el FXML de cada pantalla y entrega al controlador su ViewModel ya
 * construido (controller factory). Los ViewModels reciben solo
 * {@link IFachadaCliente} y los dos ejecutores (D-10).
 *
 * <p>Cada pantalla registra su controlador con {@link #registrar}: así las
 * vistas se agregan una por fase (11b–11d) sin tocar esta clase.</p>
 */
public final class FabricaVistas {

    /** Lo que necesita un controlador para construirse. */
    public record Contexto(IFachadaCliente fachada, Executor hiloUI, Executor fondo,
                           String servidorInicial, Consumer<TipoVista> navegar) {
    }

    private final Contexto contexto;
    private final Map<Class<?>, Function<Contexto, Object>> controladores = new ConcurrentHashMap<>();

    /**
     * @param fachada         lo único que ven los ViewModels.
     * @param hiloUI          ejecuta en el hilo de JavaFX ({@code Platform::runLater}).
     * @param fondo           ejecuta red y H2 fuera del hilo de JavaFX.
     * @param servidorInicial {@code host:puerto} de {@code cliente.properties}.
     * @param navegar         cambia de pantalla ({@link NavegadorVistas#ir}).
     */
    public FabricaVistas(IFachadaCliente fachada, Executor hiloUI, Executor fondo, String servidorInicial,
                         Consumer<TipoVista> navegar) {
        this.contexto = new Contexto(Objects.requireNonNull(fachada, "fachada"), Objects.requireNonNull(hiloUI, "hiloUI"),
                Objects.requireNonNull(fondo, "fondo"), Objects.requireNonNull(servidorInicial, "servidorInicial"),
                Objects.requireNonNull(navegar, "navegar"));
    }

    /**
     * @param controlador clase declarada en el {@code fx:controller} del FXML.
     * @param crear       construye el controlador con su ViewModel.
     * @param <T>         tipo del controlador.
     * @return esta fábrica.
     */
    public <T> FabricaVistas registrar(Class<T> controlador, Function<Contexto, T> crear) {
        controladores.put(Objects.requireNonNull(controlador, "controlador"), crear::apply);
        return this;
    }

    /**
     * @param t pantalla.
     * @return la raíz de la escena, con su controlador y ViewModel.
     * @throws IllegalStateException si la pantalla aún no existe o su FXML no es válido.
     */
    public Parent crear(TipoVista t) {
        URL fxml = FabricaVistas.class.getResource(Objects.requireNonNull(t, "t").fxml());
        if (fxml == null) {
            throw new IllegalStateException("La pantalla " + t + " aún no está disponible (falta " + t.fxml() + ")");
        }
        var loader = new FXMLLoader(fxml);
        loader.setControllerFactory(this::controlador);
        try {
            return loader.load();
        } catch (IOException e) {
            // FXMLLoader envuelve la causa real (p. ej. un controlador sin registrar) sin repetir su mensaje.
            Throwable raiz = e;
            while (raiz.getCause() != null) {
                raiz = raiz.getCause();
            }
            throw new UncheckedIOException("No se pudo cargar " + t.fxml() + ": " + raiz.getMessage(), e);
        }
    }

    /** @return el título de la ventana de la pantalla (mockups C1–C3). */
    public String titulo(TipoVista t) {
        return switch (t) {
            case LOGIN -> "Mensajería Académica — Iniciar sesión";
            case CHAT -> contexto.fachada().usuarioActual()
                    .map(u -> "Mensajería Académica — " + u.nombres() + " " + u.apellidos())
                    .orElse("Mensajería Académica");
            case INFORMES -> "Mensajería Académica — Informes del servidor";
        };
    }

    private Object controlador(Class<?> clase) {
        Function<Contexto, Object> crear = controladores.get(clase);
        if (crear == null) {
            throw new IllegalStateException("No hay un controlador registrado para " + clase.getName());
        }
        return crear.apply(contexto);
    }
}
