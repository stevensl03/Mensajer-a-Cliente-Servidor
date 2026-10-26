package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoInforme;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Estado de la pantalla C3 (cliente §5.4, mockup C3): los 4 informes del
 * servidor en una tabla paginada en el cliente, los filtros de LOGS, el JSON
 * tal como llegó (D-08) y las tarjetas de resumen.
 */
public final class InformesViewModel {

    /** Filas por página (paginación en el cliente). */
    public static final int POR_PAGINA = 50;
    /** Primera opción del combo de tipo de evento. */
    public static final String TODOS = "Todos";
    static final String FECHAS_INVERTIDAS = "La fecha «Desde» es posterior a «Hasta».";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    /**
     * Una columna de la tabla: título, cómo se lee de la fila y ancho preferido
     * (la última columna ocupa el resto).
     */
    public record Columna(String titulo, Function<Map<String, Object>, String> valor, double ancho) {

        public Columna(String titulo, Function<Map<String, Object>, String> valor) {
            this(titulo, valor, 130);
        }
    }

    private final IFachadaCliente fachada;
    private final Executor hiloUI;
    private final Clock reloj;

    private final ObjectProperty<TipoInforme> tipo = new SimpleObjectProperty<>(TipoInforme.USUARIOS_REGISTRADOS);
    private final ObservableList<Map<String, Object>> filas = FXCollections.observableArrayList();
    private final StringProperty json = new SimpleStringProperty("");
    private final IntegerProperty pagina = new SimpleIntegerProperty(0);
    private final IntegerProperty total = new SimpleIntegerProperty(0);
    private final StringProperty textoPaginacion = new SimpleStringProperty("");
    private final BooleanProperty cargando = new SimpleBooleanProperty(false);
    private final StringProperty error = new SimpleStringProperty("");
    private final StringProperty tipoEvento = new SimpleStringProperty(TODOS);
    private final StringProperty usuario = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> desde = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> hasta = new SimpleObjectProperty<>();
    private final IntegerProperty sesionesActivas = new SimpleIntegerProperty(0);
    private final IntegerProperty usuariosConectados = new SimpleIntegerProperty(0);
    private final IntegerProperty mensajesHoy = new SimpleIntegerProperty(0);
    private final IntegerProperty errores = new SimpleIntegerProperty(0);
    private final ObservableList<String> tiposEvento = FXCollections.observableArrayList(
            Stream.concat(Stream.of(TODOS), Arrays.stream(TipoEvento.values()).map(Enum::name)).toList());
    private final BooleanBinding hayAnterior = pagina.greaterThan(0);
    private final BooleanBinding haySiguiente = Bindings.createBooleanBinding(
            () -> (pagina.get() + 1) * POR_PAGINA < total.get(), pagina, total);
    /** Todas las filas del último informe (la tabla muestra una página). */
    private List<Map<String, Object>> todas = List.of();
    /** Número de la última solicitud; solo se usa en {@code hiloUI}. */
    private int solicitud;

    /**
     * @param fachada fachada del cliente.
     * @param hiloUI  ejecutor del hilo de JavaFX.
     * @param fondo   no se usa: los informes ya son asíncronos en la fachada (se recibe por simetría con D-10).
     * @param reloj   para «mensajes de hoy».
     */
    public InformesViewModel(IFachadaCliente fachada, Executor hiloUI, Executor fondo, Clock reloj) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.hiloUI = Objects.requireNonNull(hiloUI, "hiloUI");
        Objects.requireNonNull(fondo, "fondo");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        actualizarPaginacion();
    }

    /** Primera carga: usuarios registrados en la tabla y los tres informes de las tarjetas. */
    public void iniciar() {
        cargar(TipoInforme.USUARIOS_REGISTRADOS, Map.of());
        for (TipoInforme t : List.of(TipoInforme.USUARIOS_CONECTADOS, TipoInforme.MENSAJES, TipoInforme.LOGS)) {
            fachada.informe(t, Map.of()).thenAccept(r -> hiloUI.execute(() -> actualizarTarjetas(t, mapas(r))));
        }
    }

    /**
     * Pide el informe y lo muestra desde la página 1. Si mientras tanto se
     * pidió otro (otra pestaña u otro filtro), la respuesta se descarta.
     *
     * @param t       tipo de informe.
     * @param filtros filtros (solo los usa LOGS).
     */
    public void cargar(TipoInforme t, Map<String, String> filtros) {
        Objects.requireNonNull(t, "t");
        int esta = ++solicitud;
        if (tipo.get() != t) {
            // Otro informe: no se muestran sus filas con las columnas nuevas.
            todas = List.of();
            total.set(0);
            json.set("");
            irAPagina(0);
        }
        tipo.set(t);
        error.set("");
        cargando.set(true);
        fachada.informe(t, filtros == null ? Map.of() : filtros).whenComplete((r, e) -> hiloUI.execute(() -> {
            if (esta != solicitud) {
                return;
            }
            cargando.set(false);
            if (e != null) {
                error.set("No se pudo cargar el informe: " + mensaje(e));
                return;
            }
            todas = mapas(r);
            json.set(r.json());
            if (filtros == null || filtros.isEmpty()) {
                actualizarTarjetas(t, todas);
            }
            total.set(todas.size());
            irAPagina(0);
        }));
    }

    /** Botón Filtrar (pestaña Logs). */
    public void filtrar() {
        if (desde.get() != null && hasta.get() != null && desde.get().isAfter(hasta.get())) {
            error.set(FECHAS_INVERTIDAS);
            return;
        }
        cargar(TipoInforme.LOGS, filtrosLogs());
    }

    /** Vuelve a pedir el informe visible (con los filtros de LOGS si corresponde). */
    public void recargar() {
        TipoInforme t = tipo.get();
        cargar(t, t == TipoInforme.LOGS ? filtrosLogs() : Map.of());
    }

    /** @return los filtros de LOGS como parámetros del comando INFORME (R-45). */
    public Map<String, String> filtrosLogs() {
        var f = new LinkedHashMap<String, String>();
        if (tipoEvento.get() != null && !TODOS.equals(tipoEvento.get())) {
            f.put("tipoEvento", tipoEvento.get());
        }
        if (usuario.get() != null && !usuario.get().isBlank()) {
            f.put("usuario", usuario.get().strip());
        }
        if (desde.get() != null) {
            f.put("desde", desde.get().atStartOfDay().toString() + ":00");
        }
        if (hasta.get() != null) {
            f.put("hasta", hasta.get().atTime(23, 59, 59, 999_999_999).toString());
        }
        return f;
    }

    /** @param n página (desde 0); se ajusta al rango válido. */
    public void irAPagina(int n) {
        int ultima = Math.max(0, (todas.size() - 1) / POR_PAGINA);
        int p = Math.max(0, Math.min(n, ultima));
        pagina.set(p);
        filas.setAll(todas.subList(Math.min(p * POR_PAGINA, todas.size()), Math.min((p + 1) * POR_PAGINA, todas.size())));
        actualizarPaginacion();
    }

    public void paginaAnterior() {
        irAPagina(pagina.get() - 1);
    }

    public void paginaSiguiente() {
        irAPagina(pagina.get() + 1);
    }

    /** @return las columnas del informe (las mismas que el servidor, mockup S3; R-27). */
    public List<Columna> columnas(TipoInforme t) {
        return switch (t) {
            case USUARIOS_REGISTRADOS -> List.of(
                    columna("Código", "codigo"), columna("Nombres", "nombres"), columna("Apellidos", "apellidos"),
                    columna("Programa", "programa"), columna("Rol", "rol"));
            case USUARIOS_CONECTADOS -> List.of(
                    columna("Código", "codigo"), columna("Nombre", "nombreCompleto"),
                    columna("Cantidad de sesiones", "cantidadSesiones"));
            case MENSAJES -> List.of(
                    new Columna("Hora", f -> hora(f.get("fecha"))), columna("Tipo", "tipo"), columna("Canal", "canal"),
                    columna("Remitente", "remitente"),
                    new Columna("Destino", f -> f.get("destinatario") == null ? "todos" : texto(f.get("destinatario"))),
                    new Columna("Hash", f -> abreviar(texto(f.get("hash"))), 160));
            case LOGS -> List.of(
                    new Columna("Hora", f -> hora(f.get("fecha"))), new Columna("Tipo", f -> texto(f.get("tipo")), 120),
                    new Columna("Usuario", f -> texto(f.get("usuario")), 100), new Columna("IP", f -> texto(f.get("ip")), 120),
                    new Columna("Descripción", f -> texto(f.get("descripcion")), 420));
        };
    }

    /** @return la clase CSS de una fila de LOGS: ámbar para CUPO_LLENO y rojo para ERROR (mockup C3); si no, {@code null}. */
    public String estiloFila(Map<String, Object> fila) {
        if (tipo.get() != TipoInforme.LOGS || fila == null) {
            return null;
        }
        return switch (texto(fila.get("tipo"))) {
            case "CUPO_LLENO" -> "fila-cupo";
            case "ERROR" -> "fila-error";
            default -> null;
        };
    }

    private void actualizarTarjetas(TipoInforme t, List<Map<String, Object>> l) {
        switch (t) {
            case USUARIOS_CONECTADOS -> {
                usuariosConectados.set(l.size());
                sesionesActivas.set(l.stream().mapToInt(f -> entero(f.get("cantidadSesiones"))).sum());
            }
            case MENSAJES -> {
                LocalDate hoy = LocalDate.now(reloj);
                mensajesHoy.set((int) l.stream().map(f -> fecha(f.get("fecha")))
                        .filter(d -> d != null && d.toLocalDate().equals(hoy)).count());
            }
            case LOGS -> errores.set((int) l.stream().filter(f -> "ERROR".equals(texto(f.get("tipo")))).count());
            case USUARIOS_REGISTRADOS -> {
                // No alimenta ninguna tarjeta.
            }
        }
    }

    private void actualizarPaginacion() {
        int n = total.get();
        String rango = n == 0 ? "0" : (pagina.get() * POR_PAGINA + 1) + "-" + Math.min((pagina.get() + 1) * POR_PAGINA, n);
        textoPaginacion.set("Fuente: comando INFORME → JSON del servidor · " + rango + " de " + n + " registros");
    }

    /** Las filas llegan como mapas (R-19); cualquier otra cosa se descarta. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapas(InformeRecibido r) {
        var l = new ArrayList<Map<String, Object>>();
        for (Object fila : r.informe().filas()) {
            if (fila instanceof Map<?, ?> m) {
                l.add((Map<String, Object>) m);
            }
        }
        return List.copyOf(l);
    }

    private static Columna columna(String titulo, String clave) {
        return new Columna(titulo, f -> texto(f.get(clave)));
    }

    static String texto(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String abreviar(String hash) {
        return hash.length() > 12 ? hash.substring(0, 12) + "…" : hash;
    }

    private static LocalDateTime fecha(Object o) {
        try {
            return o == null ? null : LocalDateTime.parse(String.valueOf(o));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String hora(Object o) {
        LocalDateTime f = fecha(o);
        return f == null ? texto(o) : HORA.format(f);
    }

    private static int entero(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String mensaje(Throwable e) {
        Throwable causa = e;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException) && causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa instanceof ExcepcionServidor ? causa.getMessage() : String.valueOf(causa.getMessage());
    }

    public ObjectProperty<TipoInforme> tipoProperty() {
        return tipo;
    }

    /** @return la página visible. */
    public ObservableList<Map<String, Object>> filas() {
        return filas;
    }

    public StringProperty jsonProperty() {
        return json;
    }

    public IntegerProperty paginaProperty() {
        return pagina;
    }

    public IntegerProperty totalProperty() {
        return total;
    }

    public StringProperty textoPaginacionProperty() {
        return textoPaginacion;
    }

    public BooleanBinding hayAnteriorProperty() {
        return hayAnterior;
    }

    public BooleanBinding haySiguienteProperty() {
        return haySiguiente;
    }

    public BooleanProperty cargandoProperty() {
        return cargando;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public StringProperty tipoEventoProperty() {
        return tipoEvento;
    }

    public StringProperty usuarioProperty() {
        return usuario;
    }

    public ObjectProperty<LocalDate> desdeProperty() {
        return desde;
    }

    public ObjectProperty<LocalDate> hastaProperty() {
        return hasta;
    }

    public ObservableList<String> tiposEvento() {
        return tiposEvento;
    }

    public IntegerProperty sesionesActivasProperty() {
        return sesionesActivas;
    }

    public IntegerProperty usuariosConectadosProperty() {
        return usuariosConectados;
    }

    public IntegerProperty mensajesHoyProperty() {
        return mensajesHoy;
    }

    public IntegerProperty erroresProperty() {
        return errores;
    }
}
