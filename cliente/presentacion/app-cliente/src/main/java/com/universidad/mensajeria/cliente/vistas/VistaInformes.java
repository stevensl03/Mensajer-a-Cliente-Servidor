package com.universidad.mensajeria.cliente.vistas;

import com.universidad.mensajeria.cliente.viewmodels.InformesViewModel;
import com.universidad.mensajeria.dto.TipoInforme;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Objects;

/** Controlador de C3 ({@code informes.fxml}): enlaza controles con {@link InformesViewModel}. */
public final class VistaInformes {

    private static final String[] ESTILOS_FILA = {"fila-cupo", "fila-error"};

    private final InformesViewModel vm;

    @FXML
    private Label lblSesiones;
    @FXML
    private Label lblConectados;
    @FXML
    private Label lblMensajesHoy;
    @FXML
    private Label lblErrores;
    @FXML
    private TabPane tabs;
    @FXML
    private Tab tabRegistrados;
    @FXML
    private Tab tabConectados;
    @FXML
    private Tab tabMensajes;
    @FXML
    private Tab tabLogs;
    @FXML
    private VBox contenido;
    @FXML
    private HBox filtros;
    @FXML
    private ComboBox<String> cmbTipoEvento;
    @FXML
    private TextField txtUsuario;
    @FXML
    private DatePicker dpDesde;
    @FXML
    private DatePicker dpHasta;
    @FXML
    private Label lblCargando;
    @FXML
    private TableView<Map<String, Object>> tblDatos;
    @FXML
    private Label lblError;
    @FXML
    private Label lblPaginacion;
    @FXML
    private Button btnAnterior;
    @FXML
    private Button btnSiguiente;

    public VistaInformes(InformesViewModel vm) {
        this.vm = Objects.requireNonNull(vm, "vm");
    }

    @FXML
    private void initialize() {
        lblSesiones.textProperty().bind(vm.sesionesActivasProperty().asString());
        lblConectados.textProperty().bind(vm.usuariosConectadosProperty().asString());
        lblMensajesHoy.textProperty().bind(vm.mensajesHoyProperty().asString());
        lblErrores.textProperty().bind(vm.erroresProperty().asString());

        tabRegistrados.setUserData(TipoInforme.USUARIOS_REGISTRADOS);
        tabConectados.setUserData(TipoInforme.USUARIOS_CONECTADOS);
        tabMensajes.setUserData(TipoInforme.MENSAJES);
        tabLogs.setUserData(TipoInforme.LOGS);

        cmbTipoEvento.setItems(vm.tiposEvento());
        cmbTipoEvento.valueProperty().bindBidirectional(vm.tipoEventoProperty());
        txtUsuario.textProperty().bindBidirectional(vm.usuarioProperty());
        dpDesde.valueProperty().bindBidirectional(vm.desdeProperty());
        dpHasta.valueProperty().bindBidirectional(vm.hastaProperty());
        txtUsuario.setOnAction(e -> onFiltrar());

        tblDatos.setItems(vm.filas());
        tblDatos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tblDatos.setPlaceholder(new Label("Sin registros"));
        tblDatos.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Map<String, Object> fila, boolean vacia) {
                super.updateItem(fila, vacia);
                getStyleClass().removeAll(ESTILOS_FILA);
                String estilo = vacia ? null : vm.estiloFila(fila);
                if (estilo != null) {
                    getStyleClass().add(estilo);
                }
            }
        });

        lblCargando.visibleProperty().bind(vm.cargandoProperty());
        lblError.textProperty().bind(vm.errorProperty());
        lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
        lblError.managedProperty().bind(lblError.visibleProperty());
        lblPaginacion.textProperty().bind(vm.textoPaginacionProperty());
        btnAnterior.disableProperty().bind(vm.hayAnteriorProperty().not());
        btnSiguiente.disableProperty().bind(vm.haySiguienteProperty().not());

        vm.tipoProperty().addListener((o, a, t) -> mostrarColumnas(t));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, tab) -> {
            if (tab != null) {
                seleccionar(tab);
                vm.cargar((TipoInforme) tab.getUserData(), Map.of());
            }
        });

        seleccionar(tabRegistrados);
        mostrarColumnas(vm.tipoProperty().get());
        vm.iniciar();
    }

    @FXML
    public void onFiltrar() {
        vm.filtrar();
    }

    @FXML
    public void onActualizar() {
        vm.recargar();
    }

    /** Muestra el JSON del último informe con sangría, tal como llegó (D-08). */
    @FXML
    public void onVerJson() {
        var texto = new TextArea(vm.jsonProperty().get());
        texto.setEditable(false);
        texto.setWrapText(false);
        texto.getStyleClass().add("json");
        var ventana = new Stage();
        ventana.initOwner(tblDatos.getScene().getWindow());
        ventana.setTitle("JSON · " + vm.tipoProperty().get());
        var escena = new Scene(texto, 640, 520);
        escena.getStylesheets().addAll(tblDatos.getScene().getStylesheets());
        VistaChat.cerrarConEscape(escena, ventana);
        ventana.setScene(escena);
        ventana.show();
    }

    @FXML
    public void onAnterior() {
        vm.paginaAnterior();
    }

    @FXML
    public void onSiguiente() {
        vm.paginaSiguiente();
    }

    /** @param n página (desde 0). */
    public void onPagina(int n) {
        vm.irAPagina(n);
    }

    /** La tabla y los controles son únicos: se mueven a la pestaña activa. Los filtros solo aparecen en Logs. */
    private void seleccionar(Tab tab) {
        tabs.getTabs().forEach(t -> t.setContent(null));
        tab.setContent(contenido);
        boolean logs = tab == tabLogs;
        filtros.setVisible(logs);
        filtros.setManaged(logs);
    }

    private void mostrarColumnas(TipoInforme t) {
        tblDatos.getColumns().clear();
        for (InformesViewModel.Columna c : vm.columnas(t)) {
            var columna = new TableColumn<Map<String, Object>, String>(c.titulo());
            columna.setCellValueFactory(f -> new ReadOnlyStringWrapper(c.valor().apply(f.getValue())));
            tblDatos.getColumns().add(columna);
        }
    }
}
