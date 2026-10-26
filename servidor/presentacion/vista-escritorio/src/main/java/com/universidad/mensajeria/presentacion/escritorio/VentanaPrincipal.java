package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.ISerializador;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ventana Swing del servidor: barra de estado (Actualizar y Detener) y tres
 * pestañas: Monitor, Chat ADMIN e Informes.
 *
 * <p>Los avisos de la fachada llegan desde hilos de fondo: toda actualización
 * de la interfaz va por {@link SwingUtilities#invokeLater}. Cerrar la ventana
 * equivale a pulsar Detener.</p>
 */
public final class VentanaPrincipal extends JFrame implements IObservadorServidor {

    private static final Color FONDO_ACTIVO = new Color(0xE3F4E1);
    private static final Color FONDO_DETENIDO = new Color(0xF5E1E1);

    private final transient IFachadaServidor fachada;
    private final transient ISerializador json;
    private final transient Datos datos;
    private final PanelMonitor monitor;
    private final JLabel estado = new JLabel();
    private final JPanel barra = new JPanel(new BorderLayout());
    private final AtomicBoolean deteniendo = new AtomicBoolean();

    /**
     * @param fachada única puerta hacia el servidor.
     * @param json    para leer el JSON de los informes (7c-3).
     */
    public VentanaPrincipal(IFachadaServidor fachada, ISerializador json) {
        super("Servidor de Mensajería · Unillanos — ADMIN");
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        this.json = Objects.requireNonNull(json, "json");
        this.datos = Datos.leer();
        this.monitor = new PanelMonitor(fachada, datos.maxSesiones());

        estado.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        var actualizar = new JButton("⟳ Actualizar");
        actualizar.addActionListener(e -> actualizar());
        var detener = new JButton("■ Detener");
        detener.setForeground(Color.WHITE);
        detener.setBackground(new Color(0xC62828));
        detener.addActionListener(e -> confirmarDetener());
        var botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.setOpaque(false);
        botones.add(actualizar);
        botones.add(detener);
        barra.setBackground(FONDO_ACTIVO);
        barra.add(estado, BorderLayout.CENTER);
        barra.add(botones, BorderLayout.EAST);

        var pestanas = new JTabbedPane();
        pestanas.addTab("Monitor", monitor);
        pestanas.addTab("Chat ADMIN", pendiente("El chat del ADMIN llega en la fase 7c-2."));
        pestanas.addTab("Informes", pendiente("Los informes llegan en la fase 7c-3."));

        setLayout(new BorderLayout());
        add(barra, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
        setSize(1200, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmarDetener();
            }
        });
    }

    /** Muestra la ventana con los datos actuales. */
    public void iniciar() {
        SwingUtilities.invokeLater(() -> {
            actualizar();
            setVisible(true);
        });
    }

    /** Cierra la ventana (apagado ordenado). Idempotente. */
    public void detener() {
        SwingUtilities.invokeLater(this::dispose);
    }

    @Override
    public void onMensaje(MensajeDTO m) {
        SwingUtilities.invokeLater(() -> monitor.agregarMensaje(m));
    }

    @Override
    public void onEvento(EventoLogDTO e) {
        // Se cuenta aquí, en el hilo del aviso: la sesión del LOGIN ya está abierta en memoria.
        int activas = fachada.listarSesiones().size();
        SwingUtilities.invokeLater(() -> monitor.agregarEvento(e, activas));
    }

    @Override
    public void onSesiones(List<SesionDTO> l) {
        SwingUtilities.invokeLater(() -> {
            monitor.refrescarSesiones(l);
            pintarEstado(l.size());
        });
    }

    /** Vuelve a pedir las sesiones a la fachada. Se ejecuta en el hilo de Swing. */
    private void actualizar() {
        List<SesionDTO> l = fachada.listarSesiones();
        monitor.refrescarSesiones(l);
        pintarEstado(l.size());
    }

    private void pintarEstado(int activas) {
        if (deteniendo.get()) {
            return;
        }
        estado.setText(textoEstado(datos, fachada.getIpServidor(), activas));
    }

    private void confirmarDetener() {
        int r = JOptionPane.showConfirmDialog(this, "¿Detener el servidor? Se cerrarán todas las sesiones.",
                "Detener servidor", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION || !deteniendo.compareAndSet(false, true)) {
            return;
        }
        estado.setText("Deteniendo el servidor…");
        barra.setBackground(FONDO_DETENIDO);
        // detenerServidor espera a que se vacíe la cola: fuera del hilo de Swing.
        Thread.ofPlatform().name("detener-servidor").start(() -> {
            try {
                fachada.detenerServidor();
            } finally {
                SwingUtilities.invokeLater(this::dispose);
            }
        });
    }

    private static JLabel pendiente(String texto) {
        var l = new JLabel(texto, SwingConstants.CENTER);
        l.setForeground(Color.GRAY);
        return l;
    }

    /** @return {@code "Activo · Protocolo TCP · Puerto 5000 · IP … · Motor BD MYSQL · Sesiones 3 / 10"}. */
    static String textoEstado(Datos d, String ip, int activas) {
        return "Activo · Protocolo " + d.protocolo() + " · Puerto " + d.puerto() + " · IP " + ip
                + " · Motor BD " + d.motorBD() + " · Sesiones " + activas + " / " + d.maxSesiones();
    }

    /** Datos fijos de la barra de estado, leídos de la configuración cargada. */
    record Datos(String protocolo, int puerto, String motorBD, int maxSesiones) {

        static Datos leer() {
            try {
                ConfiguracionServidor cfg = ConfiguracionServidor.obtener();
                return new Datos(cfg.protocolo(), cfg.puerto(), cfg.bd().getProperty("bd.motor"), cfg.maxSesiones());
            } catch (IllegalStateException e) {
                // Sin configuración cargada (pruebas).
                return new Datos("?", 0, "?", 0);
            }
        }
    }
}
