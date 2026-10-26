package com.universidad.mensajeria.cliente.utilerias;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuración del cliente. Lee {@code cliente.properties}; si falta alguna
 * clave, el arranque falla con un mensaje que las lista todas. La instancia la
 * crea {@code AppCliente} y la reparte por constructor.
 */
public final class ConfiguracionCliente {

    /** Marcador de {@code h2.url} que se reemplaza por el código del usuario. */
    public static final String MARCADOR_CODIGO = "{codigo}";

    /** Claves obligatorias. {@code h2.clave} debe existir, pero puede estar vacía. */
    static final List<String> CLAVES = List.of(
            "servidor.host", "servidor.puerto", "servidor.protocolo", "servidor.maxTramaBytes",
            "peticion.timeoutSegundos", "h2.url", "h2.usuario", "h2.clave", "cache.mensajes.max");

    private final Properties props;

    private ConfiguracionCliente(Properties props) {
        this.props = props;
    }

    /**
     * Carga y valida la configuración.
     *
     * @param ruta ruta de {@code cliente.properties}.
     * @return la configuración cargada.
     * @throws UncheckedIOException  si el archivo no existe o no se puede leer.
     * @throws IllegalStateException si falta alguna clave o un valor es inválido.
     */
    public static ConfiguracionCliente cargar(Path ruta) {
        Objects.requireNonNull(ruta, "ruta");
        Properties p = leer(ruta);
        List<String> faltantes = CLAVES.stream()
                .filter(k -> p.getProperty(k) == null || (!k.equals("h2.clave") && p.getProperty(k).isBlank()))
                .map(k -> k + " (" + ruta.getFileName() + ")")
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Faltan claves de configuración: " + String.join(", ", faltantes));
        }
        var cargada = new ConfiguracionCliente(p);
        cargada.validar();
        return cargada;
    }

    private static Properties leer(Path archivo) {
        var props = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            props.load(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
        return props;
    }

    private void validar() {
        puerto();
        maxTramaBytes();
        timeoutSegundos();
        cacheMax();
        if (!urlH2().contains(MARCADOR_CODIGO)) {
            throw new IllegalStateException("La clave 'h2.url' debe contener " + MARCADOR_CODIGO
                    + " (una base por usuario): " + urlH2());
        }
    }

    /** @return host del servidor ({@code servidor.host}). */
    public String host() {
        return texto("servidor.host");
    }

    /** @return puerto del servidor (1 a 65535). */
    public int puerto() {
        int puerto = entero("servidor.puerto");
        if (puerto < 1 || puerto > 65_535) {
            throw new IllegalStateException("servidor.puerto fuera de rango: " + puerto);
        }
        return puerto;
    }

    /**
     * @return el nombre del protocolo ({@code servidor.protocolo}); AppCliente lo
     *         convierte a {@code TipoProtocolo} (D-03).
     */
    public String protocolo() {
        return texto("servidor.protocolo");
    }

    /** @return tamaño máximo de una trama en bytes. */
    public int maxTramaBytes() {
        return positivo("servidor.maxTramaBytes");
    }

    /** @return URL JDBC de H2 con el marcador {@value #MARCADOR_CODIGO} sin reemplazar. */
    public String urlH2() {
        return texto("h2.url");
    }

    /** @return usuario de H2. */
    public String usuarioH2() {
        return texto("h2.usuario");
    }

    /** @return clave de H2 (puede ser vacía). */
    public String claveH2() {
        return props.getProperty("h2.clave").strip();
    }

    /** @return conversaciones que guarda la caché LRU. */
    public int cacheMax() {
        return positivo("cache.mensajes.max");
    }

    /** @return segundos de espera de una respuesta del servidor. */
    public int timeoutSegundos() {
        return positivo("peticion.timeoutSegundos");
    }

    private String texto(String clave) {
        String valor = props.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la clave de configuración '" + clave + "'");
        }
        return valor.strip();
    }

    private int entero(String clave) {
        String valor = texto(clave);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("La clave '" + clave + "' debe ser entera: " + valor, e);
        }
    }

    private int positivo(String clave) {
        int valor = entero(clave);
        if (valor <= 0) {
            throw new IllegalStateException("La clave '" + clave + "' debe ser mayor que 0: " + valor);
        }
        return valor;
    }
}
