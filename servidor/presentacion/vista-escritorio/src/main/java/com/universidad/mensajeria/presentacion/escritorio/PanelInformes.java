package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.utilerias.ISerializador;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * S3 · Informes: los cuatro informes en una tabla, su JSON con sangría y, en
 * el informe de mensajes, la ficha del mensaje seleccionado con sus 6 imágenes.
 *
 * <p>Todos los métodos públicos se llaman en el hilo de Swing.</p>
 */
public final class PanelInformes extends JPanel {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Color AZUL = new Color(0x1E5AA8);
    private static final int MINIATURA = 110;
    private static final Map<TipoFiltro, String> NOMBRE_ETAPA = Map.of(
            TipoFiltro.ORIGINAL, "ORIGINAL", TipoFiltro.ESCALA_GRISES, "GRISES", TipoFiltro.SEPIA, "SEPIA",
            TipoFiltro.ROTAR_180, "ROTAR 180", TipoFiltro.BRILLO, "BRILLO", TipoFiltro.REDUCCION_TAMANO, "REDUCCIÓN");

    private final transient IFachadaServidor fachada;
    private final transient ISerializador json;
    private final DefaultTableModel modelo = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final Map<TipoInforme, JToggleButton> botones = new EnumMap<>(TipoInforme.class);
    private final JPanel detalle = new JPanel(new BorderLayout(0, 8));
    private final JSplitPane division;
    private final transient List<MensajeDTO> mensajes = new ArrayList<>();
    private final transient Map<String, String> nombres = new HashMap<>();
    private transient TipoInforme actual = TipoInforme.USUARIOS_REGISTRADOS;
    private transient String ultimoJson = "{}";

    /**
     * @param fachada para pedir los informes y las imágenes.
     * @param json    para leer el JSON de los informes y mostrarlo con sangría.
     */
    public PanelInformes(IFachadaServidor fachada, ISerializador json) {
        super(new BorderLayout(0, 6));
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.json = Objects.requireNonNull(json, "json");

        var barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        var grupo = new ButtonGroup();
        for (TipoInforme t : TipoInforme.values()) {
            var b = new JToggleButton(titulo(t));
            b.addActionListener(e -> consultar(t));
            grupo.add(b);
            botones.put(t, b);
            barra.add(b);
        }
        var actualizar = new JButton("⟳ Actualizar");
        actualizar.addActionListener(e -> consultar(actual));
        var verJson = new JButton("{ } Ver JSON");
        verJson.addActionListener(e -> verJson());
        barra.add(actualizar);
        barra.add(verJson);

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel().addListSelectionListener(e -> {
            int fila = tabla.getSelectedRow();
            if (!e.getValueIsAdjusting() && actual == TipoInforme.MENSAJES && fila >= 0) {
                detalle(mensajes.get(tabla.convertRowIndexToModel(fila)));
            }
        });
        tabla.setAutoCreateRowSorter(true);

        detalle.setBorder(BorderFactory.createTitledBorder("Detalle del mensaje seleccionado"));
        division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tabla), detalle);
        division.setResizeWeight(0.6);

        add(barra, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        // Sin datos hasta el primer consultar(): VentanaPrincipal lo pide en iniciar().
        botones.get(actual).setSelected(true);
        modelo.setColumnIdentifiers(columnas(actual).toArray());
        detalle.setVisible(false);
    }

    /** Pide el informe a la fachada y lo muestra. */
    public void consultar(TipoInforme t) {
        try {
            mostrar(t, fachada.informe(t, Map.of()));
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo generar el informe", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * @param t        tipo de informe.
     * @param jsonInforme JSON de {@code fachada.informe(t, …)}.
     */
    public void mostrar(TipoInforme t, String jsonInforme) {
        actual = t;
        ultimoJson = jsonInforme;
        botones.get(t).setSelected(true);
        botones.forEach((tipo, b) -> b.setForeground(tipo == t ? AZUL : Color.BLACK));
        nombres.clear();
        fachada.listarUsuarios().stream().map(EstadoUsuarioDTO::usuario)
                .forEach(u -> nombres.put(u.codigo(), u.nombres() + " " + u.apellidos()));

        InformeDTO informe = json.desdeJson(jsonInforme, InformeDTO.class);
        modelo.setColumnIdentifiers(columnas(t).toArray());
        modelo.setRowCount(0);
        mensajes.clear();
        if (t == TipoInforme.MENSAJES) {
            mensajes.addAll(convertir(informe, MensajeDTO.class, json));
        }
        filas(t, informe, json).forEach(modelo::addRow);

        detalle.setVisible(t == TipoInforme.MENSAJES);
        detalle.removeAll();
        detalle.add(new JLabel("Selecciona un mensaje de la tabla.", SwingConstants.CENTER), BorderLayout.CENTER);
        division.setDividerLocation(t == TipoInforme.MENSAJES ? 0.6 : 1.0);
        revalidate();
        repaint();
    }

    /** @return filas visibles de la tabla (para las pruebas). */
    int filasVisibles() {
        return modelo.getRowCount();
    }

    /** @return el valor de una celda (para las pruebas). */
    Object celda(int fila, int columna) {
        return modelo.getValueAt(fila, columna);
    }

    // --- Lógica sin interfaz gráfica ---

    /** @return el texto de cada botón. */
    static String titulo(TipoInforme t) {
        return switch (t) {
            case USUARIOS_REGISTRADOS -> "Usuarios registrados";
            case USUARIOS_CONECTADOS -> "Usuarios conectados";
            case MENSAJES -> "Mensajes";
            case LOGS -> "Logs";
        };
    }

    /** @return las columnas de la tabla según el informe (S3). */
    static List<String> columnas(TipoInforme t) {
        return switch (t) {
            case USUARIOS_REGISTRADOS -> List.of("Código", "Nombres", "Apellidos", "Programa", "Rol");
            case USUARIOS_CONECTADOS -> List.of("Código", "Nombre", "Cantidad de sesiones");
            case MENSAJES -> List.of("Hora", "Tipo", "Canal", "Remitente", "Destino", "Hash");
            case LOGS -> List.of("Hora", "Tipo", "Usuario", "IP", "Descripción");
        };
    }

    /** @return las filas de la tabla según el informe. */
    static List<Object[]> filas(TipoInforme t, InformeDTO informe, ISerializador json) {
        return switch (t) {
            case USUARIOS_REGISTRADOS -> convertir(informe, UsuarioDTO.class, json).stream()
                    .map(u -> new Object[]{u.codigo(), u.nombres(), u.apellidos(), u.programa(), u.rol()})
                    .toList();
            case USUARIOS_CONECTADOS -> convertir(informe, ConexionUsuarioDTO.class, json).stream()
                    .map(c -> new Object[]{c.codigo(), c.nombreCompleto(), c.cantidadSesiones()})
                    .toList();
            case MENSAJES -> convertir(informe, MensajeDTO.class, json).stream()
                    .map(m -> new Object[]{HORA.format(m.fecha()), m.tipo(), m.canal(), m.remitente(),
                            m.canal() == Canal.FORO ? "todos" : m.destinatario(), PanelMonitor.abreviarHash(m.hash())})
                    .toList();
            case LOGS -> convertir(informe, EventoLogDTO.class, json).stream()
                    .map(e -> new Object[]{HORA.format(e.fecha()), e.tipo(), valor(e.usuario()), valor(e.ip()),
                            e.descripcion()})
                    .toList();
        };
    }

    /** @return la ficha del mensaje (S3) como pares {atributo, valor}. */
    static List<String[]> lineasDetalle(MensajeDTO m, Map<String, String> nombres) {
        var l = new ArrayList<String[]>();
        l.add(new String[]{"ID", m.id()});
        l.add(new String[]{"Tipo", m.tipo().name()});
        l.add(new String[]{"Canal", m.canal().name()});
        l.add(new String[]{"Remitente", conNombre(m.remitente(), nombres)});
        l.add(new String[]{"Destino", m.canal() == Canal.FORO ? "Todos" : conNombre(m.destinatario(), nombres)});
        l.add(new String[]{"Fecha", FECHA.format(m.fecha())});
        l.add(new String[]{"SHA-256", m.hash()});
        if (m.tipo() == TipoMensaje.IMAGEN) {
            long bytes = m.imagenes().stream().filter(v -> v.etapa() == TipoFiltro.ORIGINAL)
                    .mapToLong(ImagenVersionDTO::tamanoBytes).findFirst().orElse(0);
            l.add(new String[]{"Archivo", m.nombreArchivo() + " (" + tamano(bytes) + ")"});
        } else {
            l.add(new String[]{"Contenido", m.numCaracteres() + " caracteres, " + m.numPalabras() + " palabras"});
        }
        return l;
    }

    /** @return {@code "<html><b>Atributo:</b> valor</html>"} con el valor escapado. */
    static String htmlLinea(String[] par) {
        return "<html><b>" + par[0] + ":</b> " + escapar(par[1]) + "</html>";
    }

    private static String conNombre(String codigo, Map<String, String> nombres) {
        String nombre = nombres.get(codigo);
        return nombre == null ? codigo : codigo + " (" + nombre + ")";
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** @return {@code "512 B"}, {@code "12,3 KB"} o {@code "1,5 MB"}. */
    static String tamano(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0).replace('.', ',');
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024)).replace('.', ',');
    }

    private static <T> List<T> convertir(InformeDTO informe, Class<T> tipo, ISerializador json) {
        return informe.filas().stream().map(f -> json.convertir(json.aArbol(f), tipo)).toList();
    }

    private static String valor(String s) {
        return s == null ? "—" : s;
    }

    // --- Interfaz ---

    /** Muestra la ficha del mensaje y, si es una imagen, sus 6 versiones. */
    private void detalle(MensajeDTO m) {
        detalle.removeAll();
        var ficha = new JPanel(new GridLayout(0, 1, 0, 2));
        for (String[] par : lineasDetalle(m, nombres)) {
            var linea = new JLabel(htmlLinea(par));
            linea.setFont(linea.getFont().deriveFont(Font.PLAIN));
            ficha.add(linea);
        }
        detalle.add(ficha, BorderLayout.NORTH);
        if (m.tipo() == TipoMensaje.IMAGEN) {
            var imagenes = new JPanel(new GridLayout(2, 3, 6, 6));
            imagenes.setBorder(BorderFactory.createTitledBorder("Imágenes generadas (filtros secuenciales)"));
            for (TipoFiltro etapa : TipoFiltro.values()) {
                var celda = new JLabel(NOMBRE_ETAPA.get(etapa), SwingConstants.CENTER);
                celda.setVerticalTextPosition(SwingConstants.BOTTOM);
                celda.setHorizontalTextPosition(SwingConstants.CENTER);
                PanelChatAdmin.cargarImagen(fachada, m.id(), etapa, MINIATURA, celda::setIcon);
                imagenes.add(celda);
            }
            detalle.add(imagenes, BorderLayout.CENTER);
            var rutas = new JLabel(htmlLinea(new String[]{"Rutas", "./almacen/originales, ./almacen/filtradas"}));
            rutas.setFont(rutas.getFont().deriveFont(Font.PLAIN));
            detalle.add(rutas, BorderLayout.SOUTH);
        } else {
            var texto = new JTextArea(m.texto());
            texto.setLineWrap(true);
            texto.setWrapStyleWord(true);
            texto.setEditable(false);
            detalle.add(new JScrollPane(texto), BorderLayout.CENTER);
        }
        detalle.revalidate();
        detalle.repaint();
    }

    private void verJson() {
        var area = new JTextArea(json.sangrar(ultimoJson));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        var dialogo = new JDialog(SwingUtilities.getWindowAncestor(this), "JSON · " + titulo(actual));
        dialogo.add(new JScrollPane(area));
        dialogo.setSize(720, 560);
        dialogo.setLocationRelativeTo(this);
        dialogo.setVisible(true);
    }
}
