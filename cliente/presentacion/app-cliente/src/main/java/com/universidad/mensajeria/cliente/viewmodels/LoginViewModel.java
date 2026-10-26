package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Estado de la pantalla C1. Conecta y hace LOGIN en el ejecutor de fondo;
 * las propiedades solo cambian en {@code hiloUI} (D-10).
 */
public final class LoginViewModel {

    static final String CREDENCIALES = "Código o contraseña incorrectos.";
    static final String CUPO = "⚠ El servidor alcanzó el cupo de sesiones. Intenta más tarde.";
    static final String SERVIDOR_INVALIDO = "Servidor inválido: escribe host:puerto (por ejemplo 127.0.0.1:5000).";

    private final StringProperty codigo = new SimpleStringProperty("");
    private final StringProperty clave = new SimpleStringProperty("");
    private final StringProperty servidor = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty ocupado = new SimpleBooleanProperty(false);
    private final ObservableList<String> protocolos;
    private final BooleanBinding puedeIngresar;
    private final IFachadaCliente fachada;
    private final Executor hiloUI;
    private final Executor fondo;
    private final Runnable alIngresar;

    /**
     * @param fachada         fachada del cliente.
     * @param hiloUI          ejecutor del hilo de JavaFX.
     * @param fondo           ejecutor para red y H2.
     * @param servidorInicial {@code host:puerto} de {@code cliente.properties}.
     * @param protocolos      nombres de {@code TipoProtocolo} (hoy solo TCP).
     * @param alIngresar      se llama en {@code hiloUI} tras un login correcto (navega a C2).
     */
    public LoginViewModel(IFachadaCliente fachada, Executor hiloUI, Executor fondo, String servidorInicial,
                          List<String> protocolos, Runnable alIngresar) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.hiloUI = Objects.requireNonNull(hiloUI, "hiloUI");
        this.fondo = Objects.requireNonNull(fondo, "fondo");
        this.alIngresar = Objects.requireNonNull(alIngresar, "alIngresar");
        this.protocolos = FXCollections.unmodifiableObservableList(FXCollections.observableArrayList(protocolos));
        servidor.set(Objects.requireNonNull(servidorInicial, "servidorInicial"));
        puedeIngresar = Bindings.createBooleanBinding(
                () -> !codigo.get().isBlank() && !clave.get().isEmpty() && !ocupado.get(), codigo, clave, ocupado);
    }

    /**
     * Conecta (si hace falta) y hace LOGIN. Ignora la llamada si no se puede ingresar.
     *
     * @return {@code true} si entró; se completa después de actualizar el estado.
     */
    public CompletableFuture<Boolean> ingresar() {
        if (!puedeIngresar.get()) {
            return CompletableFuture.completedFuture(false);
        }
        String host;
        int puerto;
        String texto = servidor.get().strip();
        int dosPuntos = texto.lastIndexOf(':');
        try {
            host = texto.substring(0, Math.max(dosPuntos, 0)).strip();
            puerto = Integer.parseInt(texto.substring(dosPuntos + 1).strip());
            if (dosPuntos <= 0 || host.isEmpty() || puerto < 1 || puerto > 65_535) {
                throw new NumberFormatException(texto);
            }
        } catch (NumberFormatException e) {
            error.set(SERVIDOR_INVALIDO);
            return CompletableFuture.completedFuture(false);
        }
        String usuario = codigo.get().strip();
        String contrasena = clave.get();
        error.set("");
        ocupado.set(true);
        var resultado = new CompletableFuture<Boolean>();
        CompletableFuture.supplyAsync(() -> fachada.conectar(host, puerto), fondo)
                .thenCompose(ip -> fachada.login(usuario, contrasena))
                .whenComplete((u, e) -> hiloUI.execute(() -> {
                    ocupado.set(false);
                    clave.set("");
                    if (e == null) {
                        alIngresar.run();
                    } else {
                        error.set(texto(e, host + ":" + puerto));
                    }
                    resultado.complete(e == null);
                }));
        return resultado;
    }

    /** Texto del área de error según la causa (mockup C1). */
    static String texto(Throwable e, String servidor) {
        Throwable causa = e;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException) && causa.getCause() != null) {
            causa = causa.getCause();
        }
        if (causa instanceof ExcepcionServidor s) {
            return switch (s.codigo()) {
                case CREDENCIALES_INVALIDAS -> CREDENCIALES;
                case CUPO_LLENO -> CUPO;
                default -> s.getMessage();
            };
        }
        if (causa.getMessage() != null && causa.getMessage().startsWith("No se pudo abrir la base local")) {
            return causa.getMessage();
        }
        if (causa instanceof UncheckedIOException || causa instanceof TimeoutException
                || causa instanceof IllegalStateException) {
            return "No fue posible conectar con " + servidor + ".";
        }
        return "Error inesperado: " + causa.getMessage();
    }

    public StringProperty codigoProperty() {
        return codigo;
    }

    public StringProperty claveProperty() {
        return clave;
    }

    public StringProperty servidorProperty() {
        return servidor;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanProperty ocupadoProperty() {
        return ocupado;
    }

    public BooleanBinding puedeIngresarProperty() {
        return puedeIngresar;
    }

    /** @return los protocolos del combo; con uno solo, el combo se deshabilita. */
    public ObservableList<String> protocolos() {
        return protocolos;
    }
}
