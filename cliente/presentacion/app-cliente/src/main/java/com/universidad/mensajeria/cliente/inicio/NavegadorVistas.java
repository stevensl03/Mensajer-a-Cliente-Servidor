package com.universidad.mensajeria.cliente.inicio;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Navegación entre pantallas. LOGIN y CHAT comparten la ventana principal;
 * INFORMES se abre en una ventana secundaria (mockups, reglas comunes), que se
 * cierra al volver a LOGIN. Se usa solo desde el hilo de JavaFX.
 */
public final class NavegadorVistas {

    /** Hoja de estilos común (mockups, reglas comunes). */
    static final String CSS = "/css/app.css";

    private final Stage stage;
    private final FabricaVistas fabrica;
    private Stage informes;

    public NavegadorVistas(Stage stage, FabricaVistas fabrica) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /**
     * @param t pantalla a mostrar.
     * @throws IllegalStateException si la pantalla no se puede cargar.
     */
    public void ir(TipoVista t) {
        Parent raiz = fabrica.crear(t);
        if (t == TipoVista.INFORMES) {
            mostrarInformes(raiz);
            return;
        }
        if (t == TipoVista.LOGIN && informes != null) {
            informes.close();
            informes = null;
        }
        stage.setScene(escena(raiz, t));
        stage.setTitle(fabrica.titulo(t));
        stage.setMinWidth(t == TipoVista.LOGIN ? t.ancho() : 800);
        stage.setMinHeight(t == TipoVista.LOGIN ? t.alto() : 560);
        stage.centerOnScreen();
        stage.show();
    }

    private void mostrarInformes(Parent raiz) {
        if (informes == null) {
            informes = new Stage();
            informes.initOwner(stage);
            informes.setOnHidden(e -> informes = null);
        }
        informes.setScene(escena(raiz, TipoVista.INFORMES));
        informes.setTitle(fabrica.titulo(TipoVista.INFORMES));
        informes.show();
        informes.toFront();
    }

    private static Scene escena(Parent raiz, TipoVista t) {
        var escena = new Scene(raiz, t.ancho(), t.alto());
        var css = NavegadorVistas.class.getResource(CSS);
        if (css != null) {
            escena.getStylesheets().add(css.toExternalForm());
        }
        return escena;
    }
}
