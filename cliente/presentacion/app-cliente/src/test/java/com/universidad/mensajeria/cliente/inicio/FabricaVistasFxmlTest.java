package com.universidad.mensajeria.cliente.inicio;

import com.universidad.mensajeria.cliente.viewmodels.FachadaFalsa;
import com.universidad.mensajeria.cliente.viewmodels.LoginViewModel;
import com.universidad.mensajeria.cliente.vistas.VistaLogin;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Carga los FXML reales con su controlador. Necesita el toolkit de JavaFX: se
 * omite si no hay pantalla (p. ej. en CI sin entorno gráfico).
 */
class FabricaVistasFxmlTest {

    private static boolean toolkit;

    @BeforeAll
    static void iniciarToolkit() throws Exception {
        boolean pantalla = System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
        if (!pantalla) {
            return;
        }
        var listo = new CompletableFuture<Void>();
        try {
            Platform.startup(() -> listo.complete(null));
        } catch (IllegalStateException yaIniciado) {
            listo.complete(null);
        } catch (UnsupportedOperationException sinPantalla) {
            return;
        }
        listo.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        toolkit = true;
    }

    @Test
    void cargaLoginConSuViewModelYEnlaces() throws Exception {
        assumeTrue(toolkit, "sin entorno gráfico");
        var fabrica = fabrica().registrar(VistaLogin.class, c -> new VistaLogin(new LoginViewModel(c.fachada(),
                c.hiloUI(), c.fondo(), c.servidorInicial(), List.of("TCP"), () -> { })));

        Parent raiz = enFx(() -> fabrica.crear(TipoVista.LOGIN));

        var servidor = (TextField) raiz.lookup("#txtServidor");
        var ingresar = (Button) raiz.lookup("#btnIngresar");
        var protocolo = (ComboBox<?>) raiz.lookup("#cmbProtocolo");
        var error = (Label) raiz.lookup("#lblError");
        assertEquals("127.0.0.1:5000", servidor.getText());
        assertTrue(ingresar.isDisabled(), "sin código ni clave");
        assertTrue(protocolo.isDisabled(), "con un solo protocolo el combo se deshabilita");
        assertEquals("TCP", protocolo.getSelectionModel().getSelectedItem());
        assertFalse(error.isVisible(), "sin error, el área está oculta");
        enFx(() -> {
            ((TextField) raiz.lookup("#txtCodigo")).setText("20201001");
            ((TextField) raiz.lookup("#txtClave")).setText("x");
            return null;
        });
        assertFalse(ingresar.isDisabled());
        assertEquals("Mensajería Académica — Iniciar sesión", fabrica.titulo(TipoVista.LOGIN));
    }

    @Test
    void unaPantallaSinControladorRegistradoFallaConClaridad() {
        assumeTrue(toolkit, "sin entorno gráfico");
        var e = assertThrows(Exception.class, () -> enFx(() -> fabrica().crear(TipoVista.LOGIN)));

        assertTrue(String.valueOf(e.getCause()).contains("VistaLogin") || String.valueOf(e).contains("VistaLogin"),
                String.valueOf(e));
    }

    private static FabricaVistas fabrica() {
        return new FabricaVistas(new FachadaFalsa(), Runnable::run, Runnable::run, "127.0.0.1:5000", t -> { });
    }

    private static <T> T enFx(Supplier<T> s) throws Exception {
        var f = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                f.complete(s.get());
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }
}
