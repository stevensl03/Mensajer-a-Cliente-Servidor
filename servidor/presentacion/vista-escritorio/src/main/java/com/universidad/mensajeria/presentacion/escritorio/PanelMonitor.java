package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.IFachadaServidor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S1 · Monitor: sesiones activas con su botón Cerrar, ocupación del cupo
 * global y mensajes y eventos entrantes en vivo (la tarjeta más reciente
 * arriba, como máximo 200).
 *
 * <p>Todos los métodos públicos se llaman en el hilo de Swing
 * ({@link VentanaPrincipal} se encarga de pasar ahí cada aviso).</p>
 */
public final class PanelMonitor extends JPanel {

    static final int MAX_TARJETAS = 200;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HORA_SEG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color AZUL = new Color(0x1E5AA8);
    private static final Color VERDE = new Color(0x2E7D32);
    private static final Color ROJO = new Color(0xC62828);
    private static final Color AMBAR = new Color(0xF2A900);
    private static final String[] COLUMNAS = {"ID", "Código", "Nombre", "IP", "Desde", ""};
    private static final int COLUMNA_CERRAR = 5;

    private final transient IFachadaServidor fachada;
    private final int maxSesiones;
    private final DefaultTableModel modeloSesiones = new DefaultTableModel(COLUMNAS, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final transient List<SesionDTO> sesiones = new ArrayList<>();
    private final DefaultListModel<String> tarjetas = new DefaultListModel<>();
    private final JProgressBar cupo = new JProgressBar();
    private final JLabel textoCupo = new JLabel();

    /**
     * @param fachada     para cerrar sesiones.
     * @param maxSesiones cupo global ({@code servidor.maxSesiones}).
     */
    public PanelMonitor(IFachadaServidor fachada, int maxSesiones) {
        super(new BorderLayout());
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.maxSesiones = maxSesiones;

        var tabla = new JTable(modeloSesiones);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getColumnModel().getColumn(COLUMNA_CERRAR).setCellRenderer(new RenderBotonCerrar());
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int fila = tabla.rowAtPoint(e.getPoint());
                if (fila >= 0 && tabla.columnAtPoint(e.getPoint()) == COLUMNA_CERRAR) {
                    cerrarSesion(sesiones.get(fila).idSesion());
                }
            }
        });
        cupo.setMaximum(Math.max(1, maxSesiones));
        cupo.setForeground(AMBAR);

        var izquierda = new JPanel(new BorderLayout(0, 6));
        izquierda.setBorder(BorderFactory.createTitledBorder(
                "Sesiones activas (cupo global maxSesiones = " + maxSesiones + ")"));
        izquierda.add(new JScrollPane(tabla), BorderLayout.CENTER);
        var pie = new JPanel(new BorderLayout(0, 4));
        pie.add(cupo, BorderLayout.NORTH);
        pie.add(textoCupo, BorderLayout.SOUTH);
        izquierda.add(pie, BorderLayout.SOUTH);

        var derecha = new JPanel(new BorderLayout());
        derecha.setBorder(BorderFactory.createTitledBorder("Mensajes entrantes (en vivo)"));
        derecha.add(new JScrollPane(new JList<>(tarjetas)), BorderLayout.CENTER);

        var division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, derecha);
        division.setResizeWeight(0.55);
        add(division, BorderLayout.CENTER);
        refrescarSesiones(List.of());
    }

    /** @param l sesiones activas (de {@code listarSesiones()} o del aviso {@code onSesiones}). */
    public void refrescarSesiones(List<SesionDTO> l) {
        sesiones.clear();
        sesiones.addAll(l);
        modeloSesiones.setRowCount(0);
        for (SesionDTO s : l) {
            modeloSesiones.addRow(new Object[]{
                    s.idSesion().substring(0, Math.min(4, s.idSesion().length())),
                    s.codigo(), s.nombreCompleto(), s.ip(), HORA.format(s.desde()), "Cerrar"});
        }
        cupo.setValue(l.size());
        textoCupo.setText(textoCupo(l, maxSesiones));
    }

    /** @param m mensaje que acaba de llegar. */
    public void agregarMensaje(MensajeDTO m) {
        agregarTarjeta(tarjeta(m));
    }

    /**
     * @param e       evento del log (solo LOGIN_OK y CUPO_LLENO generan tarjeta).
     * @param activas sesiones activas en el momento del evento.
     */
    public void agregarEvento(EventoLogDTO e, int activas) {
        tarjeta(e, activas, maxSesiones).ifPresent(this::agregarTarjeta);
    }

    /**
     * Pide confirmación y cierra la sesión con {@code fachada.cerrarSesion}.
     *
     * @param idSesion sesión a cerrar.
     */
    public void cerrarSesion(String idSesion) {
        int r = JOptionPane.showConfirmDialog(this, "¿Cerrar la sesión " + idSesion.substring(0, Math.min(4, idSesion.length())) + "…?",
                "Cerrar sesión", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            fachada.cerrarSesion(idSesion);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo cerrar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** @return el texto de las tarjetas, la más reciente primero (para las pruebas). */
    List<String> tarjetas() {
        var l = new ArrayList<String>();
        for (int i = 0; i < tarjetas.size(); i++) {
            l.add(tarjetas.get(i));
        }
        return l;
    }

    /** @return filas de la tabla de sesiones (para las pruebas). */
    int filasSesiones() {
        return modeloSesiones.getRowCount();
    }

    private void agregarTarjeta(String html) {
        tarjetas.add(0, html);
        while (tarjetas.size() > MAX_TARJETAS) {
            tarjetas.remove(tarjetas.size() - 1);
        }
    }

    // --- Textos (estáticos para poder probarlos sin pantalla) ---

    /** @return la tarjeta de un mensaje: canal y destino, contenido y hash abreviado. */
    static String tarjeta(MensajeDTO m) {
        boolean foro = m.canal() == Canal.FORO;
        String linea1 = HORA_SEG.format(m.fecha()) + " · " + m.canal() + " · " + m.remitente() + " → "
                + (foro ? "todos" : m.destinatario());
        String linea2 = m.tipo() == TipoMensaje.TEXTO
                ? "TEXTO · " + m.numCaracteres() + " car · " + m.numPalabras() + " pal"
                : "IMAGEN · " + Math.max(0, m.imagenes().size() - 1) + " filtros ✓";
        return html(foro ? AZUL : VERDE, linea1, linea2, "SHA-256 " + abreviarHash(m.hash()));
    }

    /** @return la tarjeta de LOGIN_OK o CUPO_LLENO; vacío para los demás eventos. */
    static Optional<String> tarjeta(EventoLogDTO e, int activas, int max) {
        String hora = HORA_SEG.format(e.fecha());
        String quien = valor(e.usuario()) + " · " + valor(e.ip());
        if (e.tipo() == TipoEvento.LOGIN_OK) {
            return Optional.of(html(VERDE, hora + " · LOGIN · " + quien, "LOGIN_OK", "Sesión " + activas + " de " + max));
        }
        if (e.tipo() == TipoEvento.CUPO_LLENO) {
            return Optional.of(html(ROJO, hora + " · CUPO · " + quien, "CUPO_LLENO (rechazado)",
                    "Sesiones " + max + " de " + max));
        }
        return Optional.empty();
    }

    /** @return {@code "7 de 10 sesiones · Juan Martínez tiene 2 sesiones abiertas"} (el usuario con más sesiones, si tiene más de una). */
    static String textoCupo(List<SesionDTO> l, int max) {
        String base = l.size() + " de " + max + " sesiones";
        Map<String, Long> porUsuario = l.stream()
                .collect(Collectors.groupingBy(s -> valor(s.nombreCompleto()), Collectors.counting()));
        return porUsuario.entrySet().stream()
                .filter(en -> en.getValue() > 1)
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(en -> base + " · " + en.getKey() + " tiene " + en.getValue() + " sesiones abiertas")
                .orElse(base);
    }

    /** @return {@code "abcd…wxyz"} (los primeros y los últimos 4 caracteres). */
    static String abreviarHash(String h) {
        if (h == null || h.length() <= 8) {
            return valor(h);
        }
        return h.substring(0, 4) + "…" + h.substring(h.length() - 4);
    }

    private static String html(Color color, String linea1, String linea2, String linea3) {
        Function<String, String> esc = PanelMonitor::escapar;
        return "<html><b><font color='" + String.format("#%06X", color.getRGB() & 0xFFFFFF) + "'>"
                + esc.apply(linea1) + "</font></b><br>" + esc.apply(linea2) + "<br><font color='#666666'>"
                + esc.apply(linea3) + "</font></html>";
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String valor(String s) {
        return s == null ? "—" : s;
    }

    /** Pinta la última columna como un botón rojo «Cerrar». */
    private static final class RenderBotonCerrar extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int f, int c) {
            var celda = (JLabel) super.getTableCellRendererComponent(t, v, sel, foco, f, c);
            celda.setHorizontalAlignment(CENTER);
            celda.setForeground(Color.WHITE);
            celda.setBackground(ROJO);
            celda.setOpaque(true);
            return celda;
        }
    }
}
