package com.universidad.mensajeria.cliente.vistas;

import com.universidad.mensajeria.cliente.viewmodels.ChatViewModel;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Controlador de C2 ({@code chat.fxml}): enlaza controles con
 * {@link ChatViewModel} y dibuja las celdas. No tiene lógica de negocio.
 */
public final class VistaChat {

    private static final Color VERDE = Color.web("#2E7D32");
    private static final Color ROJO = Color.web("#C62828");
    private static final Color AMBAR = Color.web("#F9A825");
    private static final Color GRIS = Color.web("#9E9E9E");
    private static final Map<TipoFiltro, String> ETAPAS = Map.of(
            TipoFiltro.ORIGINAL, "Original",
            TipoFiltro.ESCALA_GRISES, "Escala de grises",
            TipoFiltro.SEPIA, "Sepia",
            TipoFiltro.ROTAR_180, "Rotar 180°",
            TipoFiltro.BRILLO, "Brillo",
            TipoFiltro.REDUCCION_TAMANO, "Reducción de tamaño");

    private final ChatViewModel vm;
    private final Map<String, Tab> tabsPrivadas = new HashMap<>();

    @FXML
    private Label lblIniciales;
    @FXML
    private Label lblNombre;
    @FXML
    private Label lblPrograma;
    @FXML
    private Label lblConectados;
    @FXML
    private TextField txtBuscar;
    @FXML
    private ListView<EstadoUsuarioDTO> lstUsuarios;
    @FXML
    private TabPane tabs;
    @FXML
    private Tab tabForo;
    @FXML
    private ListView<MensajeDTO> lstForo;
    @FXML
    private TextField txtMensaje;
    @FXML
    private Label lblEnviando;
    @FXML
    private Button btnEnviar;
    @FXML
    private Label lblError;
    @FXML
    private Circle punto;
    @FXML
    private Label lblEstado;
    @FXML
    private Label lblResumen;

    public VistaChat(ChatViewModel vm) {
        this.vm = Objects.requireNonNull(vm, "vm");
    }

    @FXML
    private void initialize() {
        vm.iniciar();

        lblIniciales.textProperty().bind(vm.inicialesProperty());
        lblNombre.textProperty().bind(Bindings.concat(vm.nombreUsuarioProperty(), " · ", vm.codigoUsuarioProperty()));
        lblPrograma.textProperty().bind(vm.programaProperty());
        lblConectados.textProperty().bind(Bindings.format("Usuarios (%d conectados)", vm.conectadosProperty()));

        txtBuscar.textProperty().bindBidirectional(vm.filtroProperty());
        lstUsuarios.setItems(vm.usuariosVisibles());
        lstUsuarios.setCellFactory(l -> new CeldaUsuario());
        lstUsuarios.setOnMouseClicked(e -> {
            EstadoUsuarioDTO u = lstUsuarios.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && u != null) {
                onAbrirPrivado(u);
            }
        });
        // Los nombres de las pestañas llegan con la lista de usuarios.
        vm.usuarios().addListener((ListChangeListener<EstadoUsuarioDTO>) c ->
                tabsPrivadas.forEach((codigo, tab) -> tab.setText(vm.nombre(codigo))));

        tabForo.setUserData(ChatViewModel.FORO);
        prepararLista(lstForo, vm.foro());
        vm.privados().addListener((MapChangeListener<String, ObservableList<MensajeDTO>>) c -> {
            if (c.wasAdded() && !tabsPrivadas.containsKey(c.getKey())) {
                crearTab(c.getKey());
            }
        });
        vm.privados().keySet().forEach(this::crearTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, tab) -> actualizarAyuda());
        actualizarAyuda();

        btnEnviar.disableProperty().bind(txtMensaje.textProperty().isEmpty());
        lblEnviando.visibleProperty().bind(vm.enviandoProperty());
        lblError.textProperty().bind(vm.errorProperty());
        lblError.visibleProperty().bind(vm.errorProperty().isNotEmpty());
        lblError.managedProperty().bind(lblError.visibleProperty());

        punto.fillProperty().bind(Bindings.when(vm.conectadoProperty()).then(VERDE).otherwise(ROJO));
        lblEstado.textProperty().bind(vm.estadoConexionProperty());
        lblResumen.textProperty().bind(vm.resumenSesionProperty());

        Platform.runLater(txtMensaje::requestFocus);
    }

    /** Botón Enviar y Enter en el campo de escritura. */
    @FXML
    public void onEnviar() {
        String texto = txtMensaje.getText();
        if (texto == null || texto.isBlank()) {
            return;
        }
        String conversacion = conversacionActual();
        if (ChatViewModel.FORO.equals(conversacion)) {
            vm.enviarForo(texto);
        } else {
            vm.enviarPrivado(conversacion, texto);
        }
        txtMensaje.clear();
    }

    @FXML
    public void onAdjuntarImagen() {
        var chooser = new FileChooser();
        chooser.setTitle("Elegir una imagen (máximo 5 MB)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes (png, jpg, jpeg, gif, bmp)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = chooser.showOpenDialog(txtMensaje.getScene().getWindow());
        if (f != null) {
            String conversacion = conversacionActual();
            vm.enviarImagen(ChatViewModel.FORO.equals(conversacion) ? null : conversacion, f);
        }
    }

    /** Doble clic en un usuario: abre (o selecciona) su pestaña privada. */
    public void onAbrirPrivado(EstadoUsuarioDTO u) {
        String codigo = u.usuario().codigo();
        if (vm.abrirPrivado(codigo)) {
            Tab tab = crearTab(codigo);
            mostrar(tab);
            tabs.getSelectionModel().select(tab);
        }
    }

    /** Diálogo con las 6 versiones de una imagen, en orden (cada una se pide la primera vez y queda en H2). */
    public void onVerFiltros(MensajeDTO m) {
        var rejilla = new GridPane();
        rejilla.setHgap(12);
        rejilla.setVgap(12);
        rejilla.setPadding(new Insets(16));
        int i = 0;
        for (TipoFiltro etapa : TipoFiltro.values()) {
            var vista = new ImageView();
            vista.setFitWidth(220);
            vista.setFitHeight(160);
            vista.setPreserveRatio(true);
            var estado = new Label("Cargando…");
            estado.getStyleClass().add("texto-suave");
            var celda = new VBox(6, new Label((i + 1) + ". " + ETAPAS.get(etapa)), vista, estado);
            vm.imagen(m, etapa).whenComplete((img, e) -> Platform.runLater(() -> {
                vista.setImage(img);
                estado.setText(e == null ? "" : "No se pudo cargar");
            }));
            rejilla.add(celda, i % 3, i / 3);
            i++;
        }
        var ventana = new Stage();
        ventana.initOwner(txtMensaje.getScene().getWindow());
        ventana.initModality(Modality.NONE);
        ventana.setTitle("Imagen " + Objects.requireNonNullElse(m.nombreArchivo(), "") + " · 5 filtros");
        var escena = new Scene(rejilla);
        escena.getStylesheets().addAll(txtMensaje.getScene().getStylesheets());
        ventana.setScene(escena);
        ventana.show();
    }

    @FXML
    public void onInformes() {
        vm.informes();
    }

    @FXML
    public void onSalir() {
        vm.salir();
    }

    /**
     * Cada conversación privada tiene una sola pestaña, que se conserva al
     * cerrarla: si llega un mensaje nuevo, vuelve a mostrarse.
     */
    private Tab crearTab(String codigo) {
        Tab existente = tabsPrivadas.get(codigo);
        if (existente != null) {
            return existente;
        }
        ObservableList<MensajeDTO> mensajes = vm.privados().get(codigo);
        var lista = new ListView<MensajeDTO>();
        lista.getStyleClass().add("lista-mensajes");
        prepararLista(lista, mensajes);
        var tab = new Tab(vm.nombre(codigo), lista);
        tab.setUserData(codigo);
        tabsPrivadas.put(codigo, tab);
        mensajes.addListener((ListChangeListener<MensajeDTO>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    mostrar(tab);
                }
            }
        });
        mostrar(tab);
        return tab;
    }

    private void mostrar(Tab tab) {
        if (!tabs.getTabs().contains(tab)) {
            tabs.getTabs().add(tab);
        }
    }

    private void prepararLista(ListView<MensajeDTO> lista, ObservableList<MensajeDTO> mensajes) {
        lista.setItems(mensajes);
        lista.setCellFactory(l -> new CeldaMensaje());
        lista.setPlaceholder(new Label("Aún no hay mensajes"));
        mensajes.addListener((ListChangeListener<MensajeDTO>) c -> {
            if (!mensajes.isEmpty()) {
                lista.scrollTo(mensajes.size() - 1);
            }
        });
    }

    private String conversacionActual() {
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        return tab == null ? ChatViewModel.FORO : (String) tab.getUserData();
    }

    private void actualizarAyuda() {
        txtMensaje.setPromptText(vm.ayudaEscritura(conversacionActual()));
    }

    /** Usuario: punto de color (ámbar ADMIN, verde conectado, gris desconectado), nombre y programa. */
    private final class CeldaUsuario extends ListCell<EstadoUsuarioDTO> {
        @Override
        protected void updateItem(EstadoUsuarioDTO e, boolean vacia) {
            super.updateItem(e, vacia);
            setText(null);
            if (vacia || e == null) {
                setGraphic(null);
                return;
            }
            var puntoUsuario = new Circle(5, e.usuario().rol() == Rol.ADMIN ? AMBAR : e.conectado() ? VERDE : GRIS);
            var nombre = new Label(e.usuario().nombres() + " " + e.usuario().apellidos());
            nombre.getStyleClass().add("usuario-nombre");
            var programa = new Label(e.usuario().rol() == Rol.ADMIN ? "Administrador" : e.usuario().programa());
            programa.getStyleClass().add("texto-suave");
            var fila = new HBox(8, puntoUsuario, new VBox(1, nombre, programa));
            fila.setAlignment(Pos.CENTER_LEFT);
            setGraphic(fila);
        }
    }

    /** Burbuja: de otros a la izquierda y blanca; propia a la derecha y azul claro (mockup C2). */
    private final class CeldaMensaje extends ListCell<MensajeDTO> {
        @Override
        protected void updateItem(MensajeDTO m, boolean vacia) {
            super.updateItem(m, vacia);
            setText(null);
            if (vacia || m == null) {
                setGraphic(null);
                return;
            }
            boolean propio = vm.esPropio(m);
            var encabezado = new Label(vm.encabezado(m));
            encabezado.getStyleClass().add("burbuja-encabezado");
            var burbuja = new VBox(4, encabezado);
            burbuja.getStyleClass().addAll("burbuja", propio ? "burbuja-propia" : "burbuja-ajena");
            burbuja.setMaxWidth(480);
            if (m.tipo() == TipoMensaje.TEXTO) {
                var texto = new Label(m.texto());
                texto.setWrapText(true);
                burbuja.getChildren().add(texto);
            } else {
                var miniatura = new ImageView();
                miniatura.setFitWidth(220);
                miniatura.setFitHeight(160);
                miniatura.setPreserveRatio(true);
                var filtros = new Button("Ver 5 filtros");
                filtros.setOnAction(e -> onVerFiltros(m));
                burbuja.getChildren().addAll(miniatura, new Label(m.nombreArchivo()), filtros);
                vm.imagen(m, TipoFiltro.ORIGINAL).thenAccept(img -> Platform.runLater(() -> {
                    if (getItem() == m) {
                        miniatura.setImage(img);
                    }
                }));
            }
            var fila = new HBox(burbuja);
            fila.setAlignment(propio ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            fila.prefWidthProperty().bind(getListView().widthProperty().subtract(28));
            setGraphic(fila);
        }
    }
}
