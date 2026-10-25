package com.universidad.mensajeria.presentacion.consola;

import com.universidad.mensajeria.dto.SesionDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Menú 0–7 de la consola (mockup S4): lee la entrada del administrador e
 * imprime tablas e informes. Toda impresión toma el bloqueo de la consola
 * para no mezclarse con los avisos que llegan desde otros hilos.
 */
public final class MenuConsola {

    /** Lo devuelve {@link #leerOpcion()} cuando la entrada no es un número del menú. */
    public static final int OPCION_INVALIDA = -1;
    /** Lo devuelve {@link #leerOpcion()} cuando se cerró la entrada estándar. */
    public static final int FIN_DE_ENTRADA = -2;

    /** Línea del menú; si el terminal no puede mostrar «─» (p. ej. Windows-1252) se usa «-». */
    private static final char LINEA = '─';
    private static final char LINEA_ASCII = '-';
    private static final int ANCHO = 62;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final BufferedReader entrada;
    private final PrintStream salida;
    private final ReentrantLock bloqueo;
    private final String separador;

    /**
     * @param bloqueo el mismo bloqueo que usa la vista al imprimir avisos.
     */
    public MenuConsola(BufferedReader entrada, PrintStream salida, ReentrantLock bloqueo) {
        this.entrada = Objects.requireNonNull(entrada, "entrada");
        this.salida = Objects.requireNonNull(salida, "salida");
        this.bloqueo = Objects.requireNonNull(bloqueo, "bloqueo");
        char linea = salida.charset().newEncoder().canEncode(LINEA) ? LINEA : LINEA_ASCII;
        this.separador = String.valueOf(linea).repeat(ANCHO);
    }

    public void mostrarOpciones() {
        imprimir(String.join(System.lineSeparator(),
                separador,
                "  1) Ver sesiones activas          5) Publicar en el foro (ADMIN)",
                "  2) Informe: usuarios registrados 6) Mensaje privado (ADMIN)",
                "  3) Informe: usuarios conectados  7) Cerrar una sesión",
                "  4) Informe: mensajes / logs      0) Detener servidor",
                separador));
    }

    /** @return la opción 0–7, {@link #OPCION_INVALIDA} o {@link #FIN_DE_ENTRADA}. */
    public int leerOpcion() {
        Optional<String> linea = preguntar("Opción: ");
        if (linea.isEmpty()) {
            return FIN_DE_ENTRADA;
        }
        try {
            int opcion = Integer.parseInt(linea.get().trim());
            return opcion >= 0 && opcion <= 7 ? opcion : OPCION_INVALIDA;
        } catch (NumberFormatException e) {
            return OPCION_INVALIDA;
        }
    }

    /**
     * @param pregunta texto que se muestra antes de leer.
     * @return la línea escrita (sin espacios en los extremos), o vacío si se cerró la entrada.
     */
    public Optional<String> preguntar(String pregunta) {
        bloqueo.lock();
        try {
            salida.print(pregunta);
            salida.flush();
        } finally {
            bloqueo.unlock();
        }
        try {
            // La lectura queda fuera del bloqueo: si no, los avisos esperarían a que el ADMIN escriba.
            String linea = entrada.readLine();
            return Optional.ofNullable(linea).map(String::trim);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @param json informe en JSON compacto; se imprime con sangría. */
    public void imprimirInforme(String json) {
        imprimir(sangrar(json));
    }

    public void imprimirSesiones(List<SesionDTO> l) {
        if (l.isEmpty()) {
            imprimir("No hay sesiones activas.");
            return;
        }
        var t = new StringBuilder(String.format("%-3s %-6s %-10s %-24s %-15s %-5s%n",
                "#", "ID", "Código", "Nombre", "IP", "Desde"));
        for (int i = 0; i < l.size(); i++) {
            SesionDTO s = l.get(i);
            t.append(String.format("%-3d %-6s %-10s %-24s %-15s %-5s%n", i + 1,
                    s.idSesion().substring(0, Math.min(4, s.idSesion().length())),
                    s.codigo(), recortar(s.nombreCompleto(), 24), s.ip(), HORA.format(s.desde())));
        }
        t.append(l.size()).append(l.size() == 1 ? " sesión activa" : " sesiones activas");
        imprimir(t.toString());
    }

    /** Imprime un texto completo sin que se intercalen otras líneas. */
    public void imprimir(String texto) {
        bloqueo.lock();
        try {
            salida.println(texto);
            salida.flush();
        } finally {
            bloqueo.unlock();
        }
    }

    /** Sangra un JSON compacto (2 espacios por nivel), respetando las cadenas. */
    static String sangrar(String json) {
        var r = new StringBuilder();
        int nivel = 0;
        boolean enCadena = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (enCadena) {
                r.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    r.append(json.charAt(++i));
                } else if (c == '"') {
                    enCadena = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    enCadena = true;
                    r.append(c);
                }
                case '{', '[' -> {
                    char cierre = c == '{' ? '}' : ']';
                    if (i + 1 < json.length() && json.charAt(i + 1) == cierre) {
                        r.append(c).append(cierre);
                        i++;
                    } else {
                        r.append(c).append('\n').append("  ".repeat(++nivel));
                    }
                }
                case '}', ']' -> r.append('\n').append("  ".repeat(--nivel)).append(c);
                case ',' -> r.append(",\n").append("  ".repeat(nivel));
                case ':' -> r.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) {
                        r.append(c);
                    }
                }
            }
        }
        return r.toString();
    }

    private static String recortar(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
