package com.universidad.mensajeria.cliente.inicio;

import com.universidad.mensajeria.cliente.viewmodels.FachadaFalsa;
import com.universidad.mensajeria.cliente.viewmodels.ChatViewModel;
import com.universidad.mensajeria.cliente.vistas.VistaChat;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.cliente.viewmodels.LoginViewModel;
import com.universidad.mensajeria.cliente.vistas.VistaLogin;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
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
    void cargaChatConForoUsuariosBarraDeEstadoYPestanaPrivada() throws Exception {
        assumeTrue(toolkit, "sin entorno gráfico");
        var fachada = new FachadaFalsa();
        fachada.actual = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
        fachada.listaUsuarios = List.of(new EstadoUsuarioDTO(fachada.actual, true, 1),
                new EstadoUsuarioDTO(new UsuarioDTO("20201002", "Luis", "Gómez", "Electrónica", Rol.USUARIO), true, 1));
        fachada.historiales.put("FORO", List.of(new MensajeDTO("f1", TipoMensaje.TEXTO, Canal.FORO, "20201002", null,
                LocalDateTime.of(2026, 10, 25, 20, 0), "h", "Hola foro", 9, 2, null, List.of())));
        var vm = new ChatViewModel(fachada, Runnable::run, Runnable::run, Duration.ofSeconds(15), () -> { }, () -> { },
                m -> { });
        var fabrica = new FabricaVistas(fachada, Runnable::run, Runnable::run, "127.0.0.1:5000", t -> { })
                .registrar(VistaChat.class, c -> new VistaChat(vm));

        Parent raiz = enFx(() -> fabrica.crear(TipoVista.CHAT));

        var tabs = (TabPane) raiz.lookup("#tabs");
        assertEquals("Estoy conectado a: 192.168.20.31 · TCP :5000", ((Label) raiz.lookup("#lblEstado")).getText());
        assertEquals("Usuarios (2 conectados)", ((Label) raiz.lookup("#lblConectados")).getText());
        assertEquals("AP", ((Label) raiz.lookup("#lblIniciales")).getText());
        assertEquals(1, ((ListView<?>) raiz.lookup("#lstForo")).getItems().size());
        assertEquals(1, tabs.getTabs().size());
        assertEquals("Mensajería Académica — Ana Pérez", fabrica.titulo(TipoVista.CHAT));

        enFx(() -> {
            vm.onMensaje(new MensajeDTO("p1", TipoMensaje.TEXTO, Canal.PRIVADO, "20201002", "20201001",
                    LocalDateTime.of(2026, 10, 25, 20, 1), "h", "Hola Ana", 8, 2, null, List.of()));
            return null;
        });

        assertEquals(2, tabs.getTabs().size(), "un privado nuevo abre su pestaña");
        assertEquals("Luis Gómez", tabs.getTabs().get(1).getText());
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
