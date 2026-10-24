package com.universidad.mensajeria.presentacion.consola;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Vista de consola del servidor (mockup S4): imprime la información de cada
 * mensaje que llega y atiende el menú del administrador. Es la única clase
 * del servidor que escribe en {@code System.out}.
 */
public final class VistaConsola implements IObservadorServidor {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IFachadaServidor fachada;
    private final MenuConsola menu;
    private final ChatAdminConsola chat;
    private final CountDownLatch fin = new CountDownLatch(1);

    public VistaConsola(IFachadaServidor fachada) {
        this(fachada, System.in, System.out);
    }

    /** Para las pruebas: entrada y salida simuladas. */
    VistaConsola(IFachadaServidor fachada, InputStream entrada, PrintStream salida) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
        var lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        this.menu = new MenuConsola(lector, salida, new ReentrantLock());
        this.chat = new ChatAdminConsola(fachada);
    }

    /**
     * Muestra el menú y atiende al administrador hasta que elija «0». Si se
     * cierra la entrada estándar, el servidor sigue activo hasta Ctrl+C.
     */
    public void iniciar() {
        menu.imprimir(encabezado());
        while (fin.getCount() > 0) {
            menu.mostrarOpciones();
            int opcion = menu.leerOpcion();
            if (opcion == MenuConsola.FIN_DE_ENTRADA) {
                menu.imprimir("Entrada cerrada: el servidor sigue activo (Ctrl+C para detenerlo).");
                esperarFin();
                return;
            }
            try {
                if (atender(opcion)) {
                    return;
                }
            } catch (RuntimeException e) {
                // Errores esperados de la fachada (destino inexistente, sesión cerrada…): se informan y se sigue.
                menu.imprimir("Error: " + e.getMessage());
            }
        }
    }

    /** Lo llama el apagado ordenado; desbloquea {@link #iniciar()} si espera sin entrada. */
    public void detener() {
        fin.countDown();
    }

    /** Detalle de cada mensaje que llega (requisito de contexto.txt). */
    @Override
    public void onMensaje(MensajeDTO m) {
        var d = new StringBuilder("[MENSAJE ").append(HORA.format(m.fecha())).append("] ")
                .append(m.canal()).append(" · ").append(m.remitente()).append(" → ")
                .append(m.canal() == Canal.FORO ? "todos" : m.destinatario())
                .append(System.lineSeparator()).append("  ");
        if (m.tipo() == TipoMensaje.TEXTO) {
            d.append("TEXTO \"").append(m.texto()).append("\" · ")
                    .append(m.numCaracteres()).append(" car · ").append(m.numPalabras()).append(" pal");
        } else {
            d.append("IMAGEN ").append(m.nombreArchivo()).append(" · ")
                    .append(Math.max(0, m.imagenes().size() - 1)).append(" filtros");
        }
        d.append(System.lineSeparator()).append("  SHA-256 ").append(m.hash());
        menu.imprimir(d.toString());
    }

    @Override
    public void onEvento(EventoLogDTO e) {
        menu.imprimir("[" + e.tipo() + " " + HORA.format(e.fecha()) + "] " + e.descripcion());
    }

    @Override
    public void onSesiones(List<SesionDTO> l) {
        menu.imprimir("[SESIONES] " + l.size() + " activa(s)");
    }

    /** @return {@code true} si el administrador detuvo el servidor. */
    private boolean atender(int opcion) {
        switch (opcion) {
            case 1 -> menu.imprimirSesiones(fachada.listarSesiones());
            case 2 -> menu.imprimirInforme(fachada.informe(TipoInforme.USUARIOS_REGISTRADOS, Map.of()));
            case 3 -> menu.imprimirInforme(fachada.informe(TipoInforme.USUARIOS_CONECTADOS, Map.of()));
            case 4 -> informeMensajesOLogs();
            case 5 -> leer("Texto para el foro: ").ifPresent(chat::publicarEnForo);
            case 6 -> leer("Código del destinatario: ").ifPresent(codigo ->
                    leer("Texto: ").ifPresent(texto -> chat.enviarPrivado(codigo, texto)));
            case 7 -> cerrarUnaSesion();
            case 0 -> {
                return detenerServidor();
            }
            default -> menu.imprimir("Opción no válida");
        }
        return false;
    }

    private void informeMensajesOLogs() {
        Optional<String> cual = leer("¿Mensajes (m) o logs (l)? ");
        if (cual.isEmpty()) {
            return;
        }
        if (cual.get().equalsIgnoreCase("m")) {
            menu.imprimirInforme(fachada.informe(TipoInforme.MENSAJES, Map.of()));
        } else if (cual.get().equalsIgnoreCase("l")) {
            var filtros = new HashMap<String, String>();
            menu.preguntar("Tipo de evento (vacío = todos): ").filter(s -> !s.isBlank())
                    .ifPresent(t -> filtros.put("tipoEvento", t.toUpperCase(Locale.ROOT)));
            menu.preguntar("Código de usuario (vacío = todos): ").filter(s -> !s.isBlank())
                    .ifPresent(u -> filtros.put("usuario", u));
            menu.imprimirInforme(fachada.informe(TipoInforme.LOGS, filtros));
        } else {
            menu.imprimir("Opción no válida");
        }
    }

    private void cerrarUnaSesion() {
        List<SesionDTO> sesiones = fachada.listarSesiones();
        menu.imprimirSesiones(sesiones);
        if (sesiones.isEmpty()) {
            return;
        }
        Optional<String> numero = leer("Número de la sesión a cerrar: ");
        if (numero.isEmpty()) {
            return;
        }
        try {
            int i = Integer.parseInt(numero.get());
            if (i < 1 || i > sesiones.size()) {
                throw new NumberFormatException();
            }
            SesionDTO s = sesiones.get(i - 1);
            fachada.cerrarSesion(s.idSesion());
            menu.imprimir("Sesión de " + s.codigo() + " cerrada.");
        } catch (NumberFormatException e) {
            menu.imprimir("Opción no válida");
        }
    }

    private boolean detenerServidor() {
        Optional<String> r = menu.preguntar("¿Detener el servidor? (s/n): ");
        if (r.isPresent() && r.get().equalsIgnoreCase("s")) {
            fachada.detenerServidor();
            menu.imprimir("Servidor detenido.");
            fin.countDown();
            return true;
        }
        return false;
    }

    /** @return la línea escrita, o vacío si quedó en blanco o se cerró la entrada. */
    private Optional<String> leer(String pregunta) {
        return menu.preguntar(pregunta).filter(s -> !s.isBlank());
    }

    private void esperarFin() {
        try {
            fin.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String encabezado() {
        String base = "Mensajería Académica Unillanos · Servidor v1.0 · IP " + fachada.getIpServidor();
        try {
            ConfiguracionServidor cfg = ConfiguracionServidor.obtener();
            return base + " · " + cfg.protocolo() + " :" + cfg.puerto() + " · "
                    + cfg.bd().getProperty("bd.motor") + " · maxSesiones=" + cfg.maxSesiones();
        } catch (IllegalStateException e) {
            // Sin configuración cargada (pruebas): el encabezado va sin esos datos.
            return base;
        }
    }
}
