package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.fachada.IObservadorCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Estado de la pantalla C2 (cliente §5.3, mockup C2). Recibe los avisos de la
 * fachada en hilos de fondo y los aplica en {@code hiloUI} (D-10).
 *
 * <p>Es una sola instancia para toda la aplicación (la fachada no tiene
 * {@code desuscribir}): {@link #iniciar()} la prepara para cada sesión y,
 * fuera de una sesión, los avisos se ignoran.</p>
 */
public final class ChatViewModel implements IObservadorCliente {

    /** Conversación del foro. */
    public static final String FORO = "FORO";
    static final Set<String> EXTENSIONES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    static final long MAX_IMAGEN_BYTES = 5_242_880;
    static final String SIN_CONFIRMAR = "No se pudo confirmar el envío: el servidor no devolvió el mensaje.";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Comparator<MensajeDTO> CRONOLOGICO =
            Comparator.comparing(MensajeDTO::fecha).thenComparing(MensajeDTO::id);

    private final IFachadaCliente fachada;
    private final Executor hiloUI;
    private final Executor fondo;
    private final Duration esperaConfirmacion;
    private final Runnable alSalir;
    private final Runnable alInformes;
    private final Consumer<String> alPerderSesion;

    private final ObservableList<EstadoUsuarioDTO> usuarios = FXCollections.observableArrayList();
    private final StringProperty filtro = new SimpleStringProperty("");
    private final FilteredList<EstadoUsuarioDTO> filtrados = new FilteredList<>(usuarios);
    private final SortedList<EstadoUsuarioDTO> usuariosVisibles = new SortedList<>(filtrados, ordenUsuarios());
    private final IntegerBinding conectados = Bindings.createIntegerBinding(
            () -> (int) usuarios.stream().filter(EstadoUsuarioDTO::conectado).count(), usuarios);
    private final ObservableList<MensajeDTO> foro = FXCollections.observableArrayList();
    private final ObservableMap<String, ObservableList<MensajeDTO>> privados = FXCollections.observableHashMap();
    private final StringProperty estadoConexion = new SimpleStringProperty("");
    private final BooleanProperty conectado = new SimpleBooleanProperty(false);
    private final BooleanProperty enviando = new SimpleBooleanProperty(false);
    private final StringProperty error = new SimpleStringProperty("");
    private final StringProperty resumenSesion = new SimpleStringProperty("");
    private final StringProperty nombreUsuario = new SimpleStringProperty("");
    private final StringProperty codigoUsuario = new SimpleStringProperty("");
    private final StringProperty programa = new SimpleStringProperty("");
    private final StringProperty iniciales = new SimpleStringProperty("");
    private final Map<String, CompletableFuture<Image>> imagenes = new ConcurrentHashMap<>();
    /** Envíos propios que esperan su MENSAJE_NUEVO; solo se usa en {@code hiloUI}. */
    private final Deque<Object> envios = new ArrayDeque<>();
    private volatile boolean activo;
    private volatile String yo;

    /**
     * @param fachada            fachada del cliente.
     * @param hiloUI             ejecutor del hilo de JavaFX.
     * @param fondo              ejecutor para red, H2 y archivos.
     * @param esperaConfirmacion tiempo máximo hasta el MENSAJE_NUEVO de un envío propio (R-32).
     * @param alSalir            vuelve a C1 después del logout.
     * @param alInformes         abre C3.
     * @param alPerderSesion     avisa el motivo y vuelve a C1 (SESION_CERRADA o caída).
     */
    public ChatViewModel(IFachadaCliente fachada, Executor hiloUI, Executor fondo, Duration esperaConfirmacion,
                         Runnable alSalir, Runnable alInformes, Consumer<String> alPerderSesion) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.hiloUI = Objects.requireNonNull(hiloUI, "hiloUI");
        this.fondo = Objects.requireNonNull(fondo, "fondo");
        this.esperaConfirmacion = Objects.requireNonNull(esperaConfirmacion, "esperaConfirmacion");
        this.alSalir = Objects.requireNonNull(alSalir, "alSalir");
        this.alInformes = Objects.requireNonNull(alInformes, "alInformes");
        this.alPerderSesion = Objects.requireNonNull(alPerderSesion, "alPerderSesion");
        filtro.addListener((o, a, texto) -> filtrados.setPredicate(u -> coincide(u, texto)));
    }

    /** Prepara la pantalla para la sesión actual: usuarios, foro y barra de estado. Se llama en {@code hiloUI}. */
    public void iniciar() {
        UsuarioDTO u = fachada.usuarioActual().orElseThrow(() -> new IllegalStateException("No hay una sesión iniciada"));
        yo = u.codigo();
        usuarios.clear();
        foro.clear();
        privados.clear();
        imagenes.clear();
        envios.clear();
        filtro.set("");
        enviando.set(false);
        error.set("");
        nombreUsuario.set(u.nombres() + " " + u.apellidos());
        codigoUsuario.set(u.codigo());
        programa.set(u.programa());
        iniciales.set(iniciales(u));
        conectado.set(true);
        estadoConexion.set("Estoy conectado a: " + fachada.getIpServidor() + " · TCP :" + fachada.getPuerto());
        activo = true;
        fachada.usuarios().thenAccept(this::onUsuarios);
        cargar(FORO);
        actualizarResumen();
    }

    /** @param texto mensaje para el foro (se ignora si está en blanco). */
    public void enviarForo(String texto) {
        if (texto != null && !texto.isBlank()) {
            enviar(() -> fachada.enviarForo(new ContenidoDTO(TipoMensaje.TEXTO, texto, null, null)));
        }
    }

    /** @param codigo destinatario; @param texto mensaje (se ignora si está en blanco). */
    public void enviarPrivado(String codigo, String texto) {
        if (texto != null && !texto.isBlank()) {
            enviar(() -> fachada.enviarPrivado(codigo, new ContenidoDTO(TipoMensaje.TEXTO, texto, null, null)));
        }
    }

    /**
     * Valida, lee el archivo en el ejecutor de fondo y lo envía (R-50).
     *
     * @param destino código del destinatario, o {@code null} para el foro.
     * @param f       imagen elegida.
     */
    public void enviarImagen(String destino, File f) {
        String nombre = f.getName();
        int punto = nombre.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES.contains(extension)) {
            error.set("Solo se aceptan imágenes png, jpg, jpeg, gif o bmp.");
            return;
        }
        if (f.length() == 0 || f.length() > MAX_IMAGEN_BYTES) {
            error.set("La imagen debe pesar como máximo 5 MB.");
            return;
        }
        error.set("");
        fondo.execute(() -> {
            ContenidoDTO c;
            try {
                c = new ContenidoDTO(TipoMensaje.IMAGEN, null, nombre,
                        Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath())));
            } catch (IOException e) {
                hiloUI.execute(() -> error.set("No se pudo leer " + nombre + ": " + e.getMessage()));
                return;
            }
            hiloUI.execute(() -> enviar(() -> destino == null ? fachada.enviarForo(c) : fachada.enviarPrivado(destino, c)));
        });
    }

    /**
     * Crea la conversación privada (si no existe) y carga su historial. No se
     * puede abrir una conversación con uno mismo (R-34).
     *
     * @param codigo el otro usuario.
     * @return {@code true} si la conversación existe tras la llamada.
     */
    public boolean abrirPrivado(String codigo) {
        if (codigo == null || codigo.isBlank() || codigo.equals(yo)) {
            return false;
        }
        if (!privados.containsKey(codigo)) {
            privados.put(codigo, FXCollections.observableArrayList());
            cargar(codigo);
        }
        return true;
    }

    /**
     * @param m     mensaje de imagen.
     * @param etapa versión.
     * @return la imagen (de H2 o del servidor), construida fuera del hilo de JavaFX y guardada en memoria.
     */
    public CompletableFuture<Image> imagen(MensajeDTO m, TipoFiltro etapa) {
        String clave = m.id() + "|" + etapa;
        var nueva = new CompletableFuture<Image>();
        CompletableFuture<Image> existente = imagenes.putIfAbsent(clave, nueva);
        if (existente != null) {
            return existente;
        }
        // Fuera de computeIfAbsent: un futuro ya fallado no puede modificar el mapa mientras se calcula.
        fachada.imagen(m.id(), etapa)
                .thenApplyAsync(b -> new Image(new ByteArrayInputStream(b)), fondo)
                .whenComplete((i, e) -> {
                    if (e == null) {
                        nueva.complete(i);
                    } else {
                        imagenes.remove(clave, nueva);
                        nueva.completeExceptionally(e);
                    }
                });
        return nueva;
    }

    /** Botón Salir: logout en el ejecutor de fondo y vuelta a C1. */
    public void salir() {
        activo = false;
        fondo.execute(() -> {
            fachada.logout();
            hiloUI.execute(alSalir);
        });
    }

    /** Botón Informes. */
    public void informes() {
        alInformes.run();
    }

    @Override
    public void onMensaje(MensajeDTO m) {
        hiloUI.execute(() -> {
            if (!activo) {
                return;
            }
            ObservableList<MensajeDTO> lista = m.canal() == Canal.FORO ? foro : listaPrivada(otro(m));
            agregar(lista, List.of(m));
            if (esPropio(m) && envios.pollFirst() != null) {
                enviando.set(!envios.isEmpty());
            }
            actualizarResumen();
        });
    }

    @Override
    public void onUsuarios(List<EstadoUsuarioDTO> l) {
        hiloUI.execute(() -> {
            if (activo) {
                usuarios.setAll(l);
            }
        });
    }

    @Override
    public void onDesconexion(String motivo) {
        hiloUI.execute(() -> {
            if (!activo) {
                return;
            }
            activo = false;
            conectado.set(false);
            enviando.set(false);
            String texto = describir(motivo);
            estadoConexion.set("Desconectado: " + texto);
            alPerderSesion.accept(texto);
        });
    }

    /** @return {@code Tú · HH:mm} o {@code Nombre · HH:mm} (mockup C2). */
    public String encabezado(MensajeDTO m) {
        return (esPropio(m) ? "Tú" : nombre(m.remitente())) + " · " + HORA.format(m.fecha());
    }

    public boolean esPropio(MensajeDTO m) {
        return m.remitente().equals(yo);
    }

    /** @return el nombre completo del usuario, o su código si no está en la lista. */
    public String nombre(String codigo) {
        return usuarios.stream().map(EstadoUsuarioDTO::usuario).filter(u -> u.codigo().equals(codigo))
                .findFirst().map(u -> u.nombres() + " " + u.apellidos()).orElse(codigo);
    }

    /** @return el texto de ayuda del campo de escritura según la conversación. */
    public String ayudaEscritura(String conversacion) {
        return FORO.equals(conversacion) ? "Escribe un mensaje para el foro…"
                : "Escribe un mensaje para " + nombre(conversacion) + "…";
    }

    private void enviar(Supplier<CompletableFuture<Void>> envio) {
        error.set("");
        Object token = new Object();
        envios.addLast(token);
        enviando.set(true);
        envio.get().whenComplete((v, e) -> hiloUI.execute(() -> {
            if (e != null && envios.remove(token)) {
                enviando.set(!envios.isEmpty());
                error.set(mensajeDeError(e));
            }
        }));
        CompletableFuture.delayedExecutor(esperaConfirmacion.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> hiloUI.execute(() -> {
                    if (activo && envios.remove(token)) {
                        enviando.set(!envios.isEmpty());
                        error.set(SIN_CONFIRMAR);
                    }
                }));
    }

    private void cargar(String conversacion) {
        fondo.execute(() -> {
            List<MensajeDTO> l = fachada.historial(conversacion);
            hiloUI.execute(() -> {
                if (activo) {
                    agregar(FORO.equals(conversacion) ? foro : listaPrivada(conversacion), l);
                }
            });
        });
    }

    private void actualizarResumen() {
        fondo.execute(() -> {
            long sesion = fachada.sesionLocal();
            long guardados = fachada.mensajesGuardados();
            hiloUI.execute(() -> resumenSesion.set("Sesión local H2 #" + sesion + " · " + guardados + " mensajes guardados"));
        });
    }

    private ObservableList<MensajeDTO> listaPrivada(String codigo) {
        return privados.computeIfAbsent(codigo, k -> FXCollections.observableArrayList());
    }

    /** Agrega sin duplicar por id (push + historial, R-53) y deja la lista en orden cronológico. */
    private static void agregar(ObservableList<MensajeDTO> lista, List<MensajeDTO> nuevos) {
        boolean cambio = false;
        for (MensajeDTO m : nuevos) {
            if (lista.stream().noneMatch(x -> x.id().equals(m.id()))) {
                lista.add(m);
                cambio = true;
            }
        }
        if (cambio) {
            FXCollections.sort(lista, CRONOLOGICO);
        }
    }

    private String otro(MensajeDTO m) {
        return esPropio(m) ? m.destinatario() : m.remitente();
    }

    /** @return el motivo de desconexión en palabras (servidor §6.6). */
    public static String describir(String motivo) {
        return switch (motivo) {
            case "CERRADA_POR_ADMIN" -> "El administrador cerró tu sesión.";
            case "SERVIDOR_DETENIDO" -> "El servidor se detuvo.";
            default -> motivo;
        };
    }

    static String mensajeDeError(Throwable e) {
        Throwable causa = e;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException) && causa.getCause() != null) {
            causa = causa.getCause();
        }
        if (causa instanceof ExcepcionServidor || causa instanceof IllegalArgumentException) {
            return causa.getMessage();
        }
        return "No se pudo enviar: " + causa.getMessage();
    }

    private static String iniciales(UsuarioDTO u) {
        String a = u.nombres().isBlank() ? "" : u.nombres().strip().substring(0, 1);
        String b = u.apellidos().isBlank() ? "" : u.apellidos().strip().substring(0, 1);
        return (a + b).toUpperCase(Locale.ROOT);
    }

    private static boolean coincide(EstadoUsuarioDTO e, String texto) {
        if (texto == null || texto.isBlank()) {
            return true;
        }
        String t = texto.strip().toLowerCase(Locale.ROOT);
        UsuarioDTO u = e.usuario();
        return (u.nombres() + " " + u.apellidos()).toLowerCase(Locale.ROOT).contains(t) || u.codigo().contains(t);
    }

    /** ADMIN primero, luego los conectados y después los demás; alfabético en cada grupo (mockup C2). */
    static Comparator<EstadoUsuarioDTO> ordenUsuarios() {
        Collator alfabetico = Collator.getInstance(Locale.forLanguageTag("es"));
        return Comparator.<EstadoUsuarioDTO>comparingInt(e -> e.usuario().rol() == Rol.ADMIN ? 0 : e.conectado() ? 1 : 2)
                .thenComparing(e -> e.usuario().nombres() + " " + e.usuario().apellidos(), alfabetico);
    }

    public ObservableList<EstadoUsuarioDTO> usuarios() {
        return usuarios;
    }

    /** @return la lista filtrada por {@link #filtroProperty()} y ordenada para la columna izquierda. */
    public SortedList<EstadoUsuarioDTO> usuariosVisibles() {
        return usuariosVisibles;
    }

    public StringProperty filtroProperty() {
        return filtro;
    }

    public IntegerBinding conectadosProperty() {
        return conectados;
    }

    public ObservableList<MensajeDTO> foro() {
        return foro;
    }

    public ObservableMap<String, ObservableList<MensajeDTO>> privados() {
        return privados;
    }

    public StringProperty estadoConexionProperty() {
        return estadoConexion;
    }

    public BooleanProperty conectadoProperty() {
        return conectado;
    }

    public BooleanProperty enviandoProperty() {
        return enviando;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public StringProperty resumenSesionProperty() {
        return resumenSesion;
    }

    public StringProperty nombreUsuarioProperty() {
        return nombreUsuario;
    }

    public StringProperty codigoUsuarioProperty() {
        return codigoUsuario;
    }

    public StringProperty programaProperty() {
        return programa;
    }

    public StringProperty inicialesProperty() {
        return iniciales;
    }
}
