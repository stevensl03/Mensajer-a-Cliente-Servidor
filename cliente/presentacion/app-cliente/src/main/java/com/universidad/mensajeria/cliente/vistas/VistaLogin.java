package com.universidad.mensajeria.cliente.vistas;

import com.universidad.mensajeria.cliente.viewmodels.LoginViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Objects;

/** Controlador de C1 ({@code login.fxml}): solo enlaza controles con {@link LoginViewModel}. */
public final class VistaLogin {

    private final LoginViewModel vm;

    @FXML
    private TextField txtCodigo;
    @FXML
    private PasswordField txtClave;
    @FXML
    private TextField txtServidor;
    @FXML
    private ComboBox<String> cmbProtocolo;
    @FXML
    private Label lblError;
    @FXML
    private Button btnIngresar;

    public VistaLogin(LoginViewModel vm) {
        this.vm = Objects.requireNonNull(vm, "vm");
    }

    @FXML
    private void initialize() {
        txtCodigo.textProperty().bindBidirectional(vm.codigoProperty());
        txtClave.textProperty().bindBidirectional(vm.claveProperty());
        txtServidor.textProperty().bindBidirectional(vm.servidorProperty());
        cmbProtocolo.setItems(vm.protocolos());
        cmbProtocolo.getSelectionModel().selectFirst();
        cmbProtocolo.setDisable(vm.protocolos().size() <= 1);

        lblError.textProperty().bind(vm.errorProperty());
        lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
        lblError.managedProperty().bind(lblError.visibleProperty());

        btnIngresar.disableProperty().bind(vm.puedeIngresarProperty().not());
        btnIngresar.textProperty().bind(javafx.beans.binding.Bindings
                .when(vm.ocupadoProperty()).then("Conectando…").otherwise("Ingresar"));
        txtCodigo.disableProperty().bind(vm.ocupadoProperty());
        txtClave.disableProperty().bind(vm.ocupadoProperty());
        txtServidor.disableProperty().bind(vm.ocupadoProperty());

        Platform.runLater(txtCodigo::requestFocus);
    }

    /** Botón Ingresar (y Enter: es el botón por defecto). */
    @FXML
    public void onIngresar() {
        vm.ingresar();
    }
}
