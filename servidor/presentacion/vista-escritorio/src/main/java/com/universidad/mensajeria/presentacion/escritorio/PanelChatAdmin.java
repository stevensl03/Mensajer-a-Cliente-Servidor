package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.utilerias.ISerializador;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * S2 · Chat ADMIN: el administrador conversa en el foro y por privado, con
 * texto o imágenes.
 *
 * <p>Columna izquierda con los usuarios conectados; a la derecha, una pestaña
 * {@code # Foro} y una por conversación privada con burbujas (las del ADMIN a
 * la derecha). El historial se carga una vez desde el informe de mensajes y
 * los nuevos llegan por {@link #mostrarMensaje}; el mensaje propio aparece
 * cuando llega, no al pulsar Enviar.</p>
 *
 * <p>Todos los métodos públicos se llaman en el hilo de Swing.</p>
 */
public final class PanelChatAdmin extends JPanel {

    static final String FORO = "FORO";
    static final Set<String> EXTENSIONES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color BURBUJA_PROPIA = new Color(0xD6E8FA);
    private static final Color BURBUJA_AJENA = Color.WHITE;
    private static final Color FONDO_CONVERSACION = new Color(0xF2F2F2);
    private static final int MINIATURA = 160;

    private final transient IFachadaServidor fachada;
    private final transient ISerializador json;
    private final int maxTextoCaracteres;
    private final int maxImagenBytes;

    private final transient List<MensajeDTO> historial = new ArrayList<>();
    private final transient Set<String> idsConocidos = new HashSet<>();
    private final transient Map<String, String> nombres = new HashMap<>();
    private final transient List<EstadoUsuarioDTO> estados = new ArrayList<>();
    private transient String admin;

    private final JTabbedPane pestanas = new JTabbedPane();
    private final Map<String, JPanel> conversaciones = new LinkedHashMap<>();
    private final DefaultListModel<EstadoUsuarioDTO> conectados = new DefaultListModel<>();
    private final JLabel tituloConectados = new JLabel();
    private final JTextField busqueda = new JTextField();
    private final DefaultComboBoxModel<Destino> destinos = new DefaultComboBoxModel<>();
    private final JTextField texto = new JTextField();
    private final JLabel etiquetaAdjunto = new JLabel();
    private transient Adjunto adjunto;

    /** Destino del combo «Para:». */
    record Destino(String clave, String etiqueta) {
        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Imagen elegida con «📎 Imagen», aún sin enviar. */
    record Adjunto(String nombre, byte[] datos) {
    }

    /**
     * @param fachada            única puerta hacia el servidor.
     * @param json               para leer el JSON del informe de mensajes.
     * @param maxTextoCaracteres {@code mensajes.maxTextoCaracteres}.
     * @param maxImagenBytes     {@code mensajes.maxImagenBytes}.
     */
    public PanelChatAdmin(IFachadaServidor fachada, ISerializador json, int maxTextoCaracteres, int maxImagenBytes) {
        super(new BorderLayout());
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.json = Objects.requireNonNull(json, "json");
        this.maxTextoCaracteres = maxTextoCaracteres;
        this.maxImagenBytes = maxImagenBytes;

        var division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, columnaConectados(), zonaConversacion());
        division.setResizeWeight(0.22);
        add(division, BorderLayout.CENTER);
        abrirPestana(FORO, "# Foro", false);
        refrescarConectados(List.of());
    }

    // --- Operaciones públicas (catálogo) ---

    /** @param c contenido que el ADMIN publica en el foro. */
    public void publicarEnForo(ContenidoDTO c) {
        enviar(() -> fachada.publicarEnForo(c));
    }

    /**
     * @param codigo destinatario.
     * @param c      contenido.
     */
    public void enviarPrivado(String codigo, ContenidoDTO c) {
        enviar(() -> fachada.enviarPrivado(codigo, c));
    }

    /**
     * Agrega el mensaje a su conversación (abre la pestaña si es un privado
     * nuevo para el ADMIN). Ignora duplicados y los privados entre otros usuarios.
     *
     * @param m mensaje que acaba de llegar.
     */
    public void mostrarMensaje(MensajeDTO m) {
        if (!registrar(m)) {
            return;
        }
        clave(m, adminCodigo()).ifPresent(clave -> {
            if (!conversaciones.containsKey(clave)) {
                // Primer privado de ese usuario: se crea su pestaña sin quitarle el foco al ADMIN.
                abrirPestana(clave, nombres.getOrDefault(clave, clave), true);
                redibujar(clave);
            } else {
                agregarBurbuja(conversaciones.get(clave), m);
            }
        });
    }

    /** @param l todos los usuarios con su estado (de {@code listarUsuarios()}). */
    public void refrescarConectados(List<EstadoUsuarioDTO> l) {
        estados.clear();
        estados.addAll(l);
        for (EstadoUsuarioDTO e : l) {
            nombres.put(e.usuario().codigo(), nombreCompleto(e.usuario()));
            if (e.usuario().rol() == Rol.ADMIN) {
                admin = e.usuario().codigo();
            }
        }
        List<EstadoUsuarioDTO> visibles = filtrarConectados(l, admin, busqueda.getText());
        conectados.clear();
        visibles.forEach(conectados::addElement);
        long total = l.stream().filter(e -> e.conectado() && e.usuario().rol() != Rol.ADMIN).count();
        tituloConectados.setText("Conectados (" + total + " usuarios)");
    }

    /** @param informeMensajes JSON de {@code fachada.informe(MENSAJES, …)}. */
    public void cargarHistorial(String informeMensajes) {
        for (MensajeDTO m : leerInforme(informeMensajes, json)) {
            registrar(m);
        }
        for (String clave : List.copyOf(conversaciones.keySet())) {
            redibujar(clave);
        }
    }

    // --- Para las pruebas ---

    /** @return claves de las pestañas abiertas, en orden. */
    List<String> pestanasAbiertas() {
        return List.copyOf(conversaciones.keySet());
    }

    /** @return burbujas de una conversación. */
    int burbujas(String clave) {
        JPanel p = conversaciones.get(clave);
        return p == null ? 0 : (int) java.util.Arrays.stream(p.getComponents()).filter(c -> c instanceof JPanel).count();
    }

    /** @return códigos de la lista «Conectados». */
    List<String> listaConectados() {
        var l = new ArrayList<String>();
        for (int i = 0; i < conectados.size(); i++) {
            l.add(conectados.get(i).usuario().codigo());
        }
        return l;
    }

    /** Abre (o selecciona) la conversación privada con {@code codigo}. */
    void abrirConversacion(String codigo) {
        if (!conversaciones.containsKey(codigo)) {
            abrirPestana(codigo, nombres.getOrDefault(codigo, codigo), true);
            redibujar(codigo);
        }
        pestanas.setSelectedIndex(new ArrayList<>(conversaciones.keySet()).indexOf(codigo));
    }

    // --- Lógica sin interfaz gráfica ---

    /**
     * @return {@code FORO}, el código del otro participante si el ADMIN está en
     *         el privado, o vacío si es un privado entre otros usuarios.
     */
    static Optional<String> clave(MensajeDTO m, String admin) {
        if (m.canal() == Canal.FORO) {
            return Optional.of(FORO);
        }
        if (admin == null) {
            return Optional.empty();
        }
        if (admin.equals(m.remitente())) {
            return Optional.ofNullable(m.destinatario());
        }
        if (admin.equals(m.destinatario())) {
            return Optional.of(m.remitente());
        }
        return Optional.empty();
    }

    /** @return los mensajes del informe {@code MENSAJES} ya convertidos. */
    static List<MensajeDTO> leerInforme(String informe, ISerializador json) {
        InformeDTO i = json.desdeJson(informe, InformeDTO.class);
        return i.filas().stream().map(f -> json.convertir(json.aArbol(f), MensajeDTO.class)).toList();
    }

    /** @return los conectados (sin el ADMIN) cuyo nombre o código contiene {@code busqueda}. */
    static List<EstadoUsuarioDTO> filtrarConectados(List<EstadoUsuarioDTO> l, String admin, String busqueda) {
        String b = busqueda == null ? "" : busqueda.strip().toLowerCase(Locale.ROOT);
        return l.stream()
                .filter(EstadoUsuarioDTO::conectado)
                .filter(e -> !e.usuario().codigo().equals(admin) && e.usuario().rol() != Rol.ADMIN)
                .filter(e -> b.isEmpty() || nombreCompleto(e.usuario()).toLowerCase(Locale.ROOT).contains(b)
                        || e.usuario().codigo().toLowerCase(Locale.ROOT).contains(b))
                .toList();
    }

    /** @return un mensaje de error si el adjunto no es válido, o vacío. */
    static Optional<String> validarAdjunto(String nombre, byte[] datos, int maxBytes) {
        int punto = nombre.lastIndexOf('.');
        String ext = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES.contains(ext)) {
            return Optional.of("Extensión no permitida: use png, jpg, jpeg, gif o bmp.");
        }
        if (datos.length == 0 || datos.length > maxBytes) {
            return Optional.of("La imagen debe pesar entre 1 byte y " + (maxBytes / 1024) + " KB.");
        }
        if (leerImagen(datos) == null) {
            return Optional.of("El archivo no es una imagen legible.");
        }
        return Optional.empty();
    }

    /** @return un mensaje de error si el texto no es válido, o vacío. */
    static Optional<String> validarTexto(String t, int maxCaracteres) {
        if (t == null || t.isBlank()) {
            return Optional.of("Escribe un mensaje.");
        }
        int caracteres = t.codePointCount(0, t.length());
        if (caracteres > maxCaracteres) {
            return Optional.of("El texto tiene " + caracteres + " caracteres (máximo " + maxCaracteres + ").");
        }
        return Optional.empty();
    }

    /** @return {@code "Nombre · HH:mm"}. */
    static String encabezado(String nombre, MensajeDTO m) {
        return nombre + " · " + HORA.format(m.fecha());
    }

    // --- Construcción de la interfaz ---

    private JPanel columnaConectados() {
        var lista = new JList<>(conectados);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foco) {
                var e = (EstadoUsuarioDTO) v;
                String s = e.sesiones() == 1 ? "1 sesión" : e.sesiones() + " sesiones";
                return super.getListCellRendererComponent(l, "<html><font color='#2E7D32'>●</font> "
                        + escapar(nombreCompleto(e.usuario())) + "<br><font color='#777777'>" + s + "</font></html>",
                        i, sel, foco);
            }
        });
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) {
                abrirConversacion(lista.getSelectedValue().usuario().codigo());
                lista.clearSelection();
            }
        });
        busqueda.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refrescarConectados(List.copyOf(estados));
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refrescarConectados(List.copyOf(estados));
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refrescarConectados(List.copyOf(estados));
            }
        });
        busqueda.setToolTipText("Buscar por nombre o código");
        var arriba = new JPanel(new BorderLayout(0, 4));
        arriba.add(tituloConectados, BorderLayout.NORTH);
        arriba.add(busqueda, BorderLayout.SOUTH);
        var columna = new JPanel(new BorderLayout(0, 6));
        columna.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        columna.add(arriba, BorderLayout.NORTH);
        columna.add(new JScrollPane(lista), BorderLayout.CENTER);
        return columna;
    }

    private JPanel zonaConversacion() {
        pestanas.addChangeListener(e -> sincronizarDestino());
        var para = new JComboBox<>(destinos);
        var imagen = new JButton("📎 Imagen");
        imagen.addActionListener(e -> elegirImagen());
        var enviar = new JButton("Enviar ➤");
        enviar.setBackground(new Color(0x1E5AA8));
        enviar.setForeground(Color.WHITE);
        enviar.addActionListener(e -> enviarLoEscrito((Destino) para.getSelectedItem()));
        texto.addActionListener(e -> enviarLoEscrito((Destino) para.getSelectedItem()));
        texto.setToolTipText("Escribe un mensaje…");

        var barra = new JPanel(new BorderLayout(6, 0));
        barra.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        var izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        izquierda.add(new JLabel("Para:"));
        izquierda.add(para);
        var derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        derecha.add(etiquetaAdjunto);
        derecha.add(imagen);
        derecha.add(enviar);
        barra.add(izquierda, BorderLayout.WEST);
        barra.add(texto, BorderLayout.CENTER);
        barra.add(derecha, BorderLayout.EAST);

        var zona = new JPanel(new BorderLayout());
        zona.add(pestanas, BorderLayout.CENTER);
        zona.add(barra, BorderLayout.SOUTH);
        return zona;
    }

    private void abrirPestana(String clave, String titulo, boolean cerrable) {
        var conversacion = new JPanel();
        conversacion.setLayout(new BoxLayout(conversacion, BoxLayout.Y_AXIS));
        conversacion.setBackground(FONDO_CONVERSACION);
        conversaciones.put(clave, conversacion);
        var scroll = new JScrollPane(conversacion);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        pestanas.addTab(titulo, scroll);
        destinos.addElement(new Destino(clave, FORO.equals(clave) ? "Foro" : titulo));
        if (cerrable) {
            var cabecera = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            cabecera.setOpaque(false);
            cabecera.add(new JLabel(titulo));
            var cerrar = new JButton("✕");
            cerrar.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            cerrar.setContentAreaFilled(false);
            cerrar.addActionListener(e -> cerrarPestana(clave));
            cabecera.add(cerrar);
            pestanas.setTabComponentAt(pestanas.getTabCount() - 1, cabecera);
        }
    }

    private void cerrarPestana(String clave) {
        int i = new ArrayList<>(conversaciones.keySet()).indexOf(clave);
        if (i <= 0) {
            return;
        }
        conversaciones.remove(clave);
        pestanas.removeTabAt(i);
        destinos.removeElementAt(i);
    }

    private void sincronizarDestino() {
        int i = pestanas.getSelectedIndex();
        if (i >= 0 && i < destinos.getSize()) {
            destinos.setSelectedItem(destinos.getElementAt(i));
        }
    }

    private void redibujar(String clave) {
        JPanel p = conversaciones.get(clave);
        p.removeAll();
        historial.stream()
                .filter(m -> clave(m, adminCodigo()).filter(clave::equals).isPresent())
                .sorted(java.util.Comparator.comparing(MensajeDTO::fecha))
                .forEach(m -> agregarBurbuja(p, m));
        p.revalidate();
        p.repaint();
    }

    private void agregarBurbuja(JPanel conversacion, MensajeDTO m) {
        boolean propia = m.remitente().equals(adminCodigo());
        var burbuja = new JPanel(new BorderLayout(0, 4));
        burbuja.setBackground(propia ? BURBUJA_PROPIA : BURBUJA_AJENA);
        burbuja.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        String nombre = propia ? "ADMIN (tú)" : nombres.getOrDefault(m.remitente(), m.remitente());
        var cabecera = new JLabel(encabezado(nombre, m));
        cabecera.setForeground(Color.DARK_GRAY);
        burbuja.add(cabecera, BorderLayout.NORTH);
        if (m.tipo() == TipoMensaje.TEXTO) {
            burbuja.add(new JLabel(htmlTexto(m.texto())), BorderLayout.CENTER);
        } else {
            var miniatura = new JLabel("Cargando " + m.nombreArchivo() + "…");
            cargarImagen(m.id(), TipoFiltro.ORIGINAL, MINIATURA, icono -> {
                miniatura.setText(null);
                miniatura.setIcon(icono);
                conversacion.revalidate();
                conversacion.repaint();
            });
            var filtros = new JButton("+5 filtros");
            filtros.addActionListener(e -> mostrarFiltros(m));
            burbuja.add(miniatura, BorderLayout.CENTER);
            burbuja.add(filtros, BorderLayout.SOUTH);
        }
        var fila = new FilaBurbuja(propia);
        fila.add(burbuja);
        conversacion.add(fila);
        conversacion.add(Box.createVerticalStrut(2));
        conversacion.revalidate();
        conversacion.repaint();
    }

    /**
     * Fila de una burbuja: ocupa todo el ancho pero solo el alto que necesita,
     * calculado en cada momento (crece cuando llega la miniatura).
     */
    private static final class FilaBurbuja extends JPanel {
        FilaBurbuja(boolean propia) {
            super(new FlowLayout(propia ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 4));
            setOpaque(false);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Ancho a partir del cual el texto de una burbuja se ajusta en varias líneas. */
    static final int CARACTERES_POR_LINEA = 60;

    /** @return el texto en HTML; solo los textos largos llevan ancho fijo (así los cortos no ocupan media pantalla). */
    static String htmlTexto(String texto) {
        String html = escapar(texto);
        boolean largo = texto != null && texto.lines().anyMatch(l -> l.length() > CARACTERES_POR_LINEA);
        return largo ? "<html><div style='width:360px'>" + html + "</div></html>" : "<html>" + html + "</html>";
    }

    /** Diálogo con las 6 imágenes del mensaje (ORIGINAL y los 5 filtros, en orden). */
    private void mostrarFiltros(MensajeDTO m) {
        var dialogo = new JDialog(SwingUtilities.getWindowAncestor(this), "Imágenes de " + m.nombreArchivo());
        var rejilla = new JPanel(new GridLayout(2, 3, 8, 8));
        rejilla.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (TipoFiltro etapa : TipoFiltro.values()) {
            var celda = new JLabel(etapa.name(), SwingConstants.CENTER);
            celda.setVerticalTextPosition(SwingConstants.BOTTOM);
            celda.setHorizontalTextPosition(SwingConstants.CENTER);
            cargarImagen(m.id(), etapa, 220, celda::setIcon);
            rejilla.add(celda);
        }
        dialogo.add(rejilla);
        dialogo.setSize(760, 560);
        dialogo.setLocationRelativeTo(this);
        dialogo.setVisible(true);
    }

    /** Pide la imagen a la fachada fuera del hilo de Swing y la muestra escalada. */
    private void cargarImagen(String id, TipoFiltro etapa, int lado, Consumer<ImageIcon> destino) {
        Thread.ofVirtual().start(() -> fachada.imagen(id, etapa).map(PanelChatAdmin::leerImagen).ifPresent(img -> {
            double escala = Math.min(1.0, (double) lado / Math.max(img.getWidth(), img.getHeight()));
            Image escalada = img.getScaledInstance((int) Math.max(1, img.getWidth() * escala),
                    (int) Math.max(1, img.getHeight() * escala), Image.SCALE_SMOOTH);
            SwingUtilities.invokeLater(() -> destino.accept(new ImageIcon(escalada)));
        }));
    }

    private void elegirImagen() {
        var selector = new JFileChooser();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path archivo = selector.getSelectedFile().toPath();
        try {
            byte[] datos = Files.readAllBytes(archivo);
            String nombre = archivo.getFileName().toString();
            Optional<String> error = validarAdjunto(nombre, datos, maxImagenBytes);
            if (error.isPresent()) {
                JOptionPane.showMessageDialog(this, error.get(), "Imagen no válida", JOptionPane.WARNING_MESSAGE);
                return;
            }
            adjunto = new Adjunto(nombre, datos);
            etiquetaAdjunto.setText("📎 " + nombre + " (" + Math.max(1, datos.length / 1024) + " KB)");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo leer el archivo: " + e.getMessage(),
                    "Imagen no válida", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enviarLoEscrito(Destino destino) {
        if (destino == null) {
            return;
        }
        String t = texto.getText();
        if (adjunto == null) {
            Optional<String> error = validarTexto(t, maxTextoCaracteres);
            if (error.isPresent()) {
                JOptionPane.showMessageDialog(this, error.get(), "Mensaje no válido", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (adjunto != null) {
            var imagen = new ContenidoDTO(TipoMensaje.IMAGEN, null, adjunto.nombre(),
                    Base64.getEncoder().encodeToString(adjunto.datos()));
            if (!enviarA(destino, imagen)) {
                return;
            }
            adjunto = null;
            etiquetaAdjunto.setText("");
        }
        if (t != null && !t.isBlank() && enviarA(destino, new ContenidoDTO(TipoMensaje.TEXTO, t, null, null))) {
            texto.setText("");
        }
    }

    private boolean enviarA(Destino destino, ContenidoDTO c) {
        return FORO.equals(destino.clave())
                ? enviar(() -> fachada.publicarEnForo(c))
                : enviar(() -> fachada.enviarPrivado(destino.clave(), c));
    }

    private boolean enviar(Runnable envio) {
        try {
            envio.run();
            return true;
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo enviar", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean registrar(MensajeDTO m) {
        if (!idsConocidos.add(m.id())) {
            return false;
        }
        historial.add(m);
        return true;
    }

    private String adminCodigo() {
        if (admin == null) {
            fachada.listarUsuarios().stream()
                    .filter(e -> e.usuario().rol() == Rol.ADMIN)
                    .findFirst()
                    .ifPresent(e -> admin = e.usuario().codigo());
        }
        return admin;
    }

    private static BufferedImage leerImagen(byte[] datos) {
        try {
            return ImageIO.read(new ByteArrayInputStream(datos));
        } catch (IOException e) {
            return null;
        }
    }

    private static String nombreCompleto(UsuarioDTO u) {
        return u.nombres() + " " + u.apellidos();
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
