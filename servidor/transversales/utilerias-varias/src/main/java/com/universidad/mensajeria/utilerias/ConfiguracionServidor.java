package com.universidad.mensajeria.utilerias;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuración del servidor (Singleton). Lee {@code servidor.properties} y
 * {@code bd.properties}; si falta alguna clave, el arranque falla con un
 * mensaje que las lista todas.
 */
public final class ConfiguracionServidor {

    /** Claves obligatorias de {@code servidor.properties}. */
    static final List<String> CLAVES_SERVIDOR = List.of(
            "servidor.protocolo", "servidor.puerto", "servidor.maxSesiones", "servidor.maxTramaBytes",
            "servidor.vista", "sesion.colaSalida", "cola.capacidad", "cola.hilosConsumidores",
            "mensajes.maxTextoCaracteres", "mensajes.maxImagenBytes", "archivos.directorio",
            "usuarios.archivoInicial", "cache.l1.max", "cache.l1.ttlMinutos", "cache.l2.habilitada",
            "redis.host", "redis.puerto", "redis.ttlMinutos");

    /** Claves obligatorias de {@code bd.properties}. */
    static final List<String> CLAVES_BD = List.of("bd.motor", "bd.url", "bd.usuario", "bd.clave", "bd.pool");

    private static volatile ConfiguracionServidor instancia;

    private final Properties props;
    private final Properties bd;

    private ConfiguracionServidor(Properties props, Properties bd) {
        this.props = props;
        this.bd = bd;
    }

    /**
     * Carga y valida la configuración, y la deja como instancia única.
     *
     * @param servidor ruta de {@code servidor.properties}.
     * @param bd       ruta de {@code bd.properties}.
     * @return la configuración cargada.
     * @throws UncheckedIOException  si falta algún archivo o no se puede leer.
     * @throws IllegalStateException si falta alguna clave o un valor numérico es inválido.
     */
    public static ConfiguracionServidor cargar(Path servidor, Path bd) {
        Objects.requireNonNull(servidor, "servidor");
        Objects.requireNonNull(bd, "bd");
        Properties ps = leer(servidor);
        Properties pb = leer(bd);
        var faltantes = new ArrayList<String>();
        faltantes.addAll(faltantes(ps, CLAVES_SERVIDOR, servidor));
        faltantes.addAll(faltantes(pb, CLAVES_BD, bd));
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Faltan claves de configuración: " + String.join(", ", faltantes));
        }
        var cargada = new ConfiguracionServidor(ps, pb);
        cargada.validarNumeros();
        instancia = cargada;
        return cargada;
    }

    /**
     * @return la configuración cargada previamente con {@link #cargar(Path, Path)}.
     * @throws IllegalStateException si aún no se ha cargado.
     */
    public static ConfiguracionServidor obtener() {
        var actual = instancia;
        if (actual == null) {
            throw new IllegalStateException("La configuración no se ha cargado");
        }
        return actual;
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

    private static List<String> faltantes(Properties p, List<String> claves, Path archivo) {
        return claves.stream()
                .filter(k -> p.getProperty(k) == null || p.getProperty(k).isBlank())
                .map(k -> k + " (" + archivo.getFileName() + ")")
                .toList();
    }

    private void validarNumeros() {
        puerto();
        maxSesiones();
        maxTramaBytes();
        colaSalida();
        colaCapacidad();
        hilosConsumidores();
        maxTextoCaracteres();
        maxImagenBytes();
        positivo(bd, "bd.pool");
    }

    /** @return el nombre del protocolo ({@code servidor.protocolo}); ServidorApp lo convierte a {@code TipoProtocolo}. */
    public String protocolo() {
        return texto(props, "servidor.protocolo");
    }

    /** @return puerto de escucha (0 a 65535). */
    public int puerto() {
        int puerto = entero(props, "servidor.puerto");
        if (puerto < 0 || puerto > 65_535) {
            throw new IllegalStateException("servidor.puerto fuera de rango: " + puerto);
        }
        return puerto;
    }

    /** @return cupo global de sesiones. */
    public int maxSesiones() {
        return positivo(props, "servidor.maxSesiones");
    }

    /** @return tamaño máximo de una trama en bytes. */
    public int maxTramaBytes() {
        return positivo(props, "servidor.maxTramaBytes");
    }

    /** @return {@code CONSOLA} o {@code ESCRITORIO}; la presentación lo traduce a su enum. */
    public String vista() {
        return texto(props, "servidor.vista");
    }

    /** @return mensajes pendientes por canal antes de cerrar a un cliente lento. */
    public int colaSalida() {
        return positivo(props, "sesion.colaSalida");
    }

    /** @return capacidad de la cola de mensajes entrantes. */
    public int colaCapacidad() {
        return positivo(props, "cola.capacidad");
    }

    /** @return hilos consumidores de la cola. */
    public int hilosConsumidores() {
        return positivo(props, "cola.hilosConsumidores");
    }

    /** @return máximo de caracteres de un texto. */
    public int maxTextoCaracteres() {
        return positivo(props, "mensajes.maxTextoCaracteres");
    }

    /** @return máximo de bytes de una imagen decodificada. */
    public int maxImagenBytes() {
        return positivo(props, "mensajes.maxImagenBytes");
    }

    /** @return raíz del directorio de archivos. */
    public Path directorioArchivos() {
        return Path.of(texto(props, "archivos.directorio"));
    }

    /** @return archivo plano con la carga inicial de usuarios. */
    public Path archivoUsuarios() {
        return Path.of(texto(props, "usuarios.archivoInicial"));
    }

    /** @return una copia de las claves de {@code bd.properties}. */
    public Properties bd() {
        var copia = new Properties();
        copia.putAll(bd);
        return copia;
    }

    /** @return una copia de las claves {@code cache.*} y {@code redis.*}. */
    public Properties cache() {
        var copia = new Properties();
        props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("cache.") || k.startsWith("redis."))
                .forEach(k -> copia.setProperty(k, props.getProperty(k).strip()));
        return copia;
    }

    private static String texto(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la clave de configuración '" + clave + "'");
        }
        return valor.strip();
    }

    private static int entero(Properties p, String clave) {
        String valor = texto(p, clave);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("La clave '" + clave + "' debe ser entera: " + valor, e);
        }
    }

    private static int positivo(Properties p, String clave) {
        int valor = entero(p, clave);
        if (valor <= 0) {
            throw new IllegalStateException("La clave '" + clave + "' debe ser mayor que 0: " + valor);
        }
        return valor;
    }
}
