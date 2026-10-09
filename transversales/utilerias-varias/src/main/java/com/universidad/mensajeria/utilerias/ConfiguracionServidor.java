package com.universidad.mensajeria.utilerias;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuración del servidor (Singleton). Lee {@code servidor.properties} y
 * {@code bd.properties} de un directorio; las claves siguen la nota de
 * configuración del diagrama.
 */
public final class ConfiguracionServidor {

    public static final String ARCHIVO_SERVIDOR = "servidor.properties";
    public static final String ARCHIVO_BD = "bd.properties";
    public static final int MAX_TRAMA_BYTES_POR_DEFECTO = 16 * 1024 * 1024;

    private static volatile ConfiguracionServidor instancia;

    private final Properties props;

    private ConfiguracionServidor(Properties props) {
        this.props = props;
    }

    /**
     * Carga la configuración y la deja como instancia única.
     *
     * @param dirConfig directorio con {@code servidor.properties} y {@code bd.properties}.
     * @return la configuración cargada.
     * @throws UncheckedIOException si falta algún archivo o no se puede leer.
     */
    public static ConfiguracionServidor cargar(Path dirConfig) {
        Objects.requireNonNull(dirConfig, "dirConfig");
        var props = new Properties();
        leer(dirConfig.resolve(ARCHIVO_SERVIDOR), props);
        leer(dirConfig.resolve(ARCHIVO_BD), props);
        var cargada = new ConfiguracionServidor(props);
        instancia = cargada;
        return cargada;
    }

    /**
     * @return la configuración cargada previamente con {@link #cargar(Path)}.
     * @throws IllegalStateException si aún no se ha cargado.
     */
    public static ConfiguracionServidor obtener() {
        var actual = instancia;
        if (actual == null) {
            throw new IllegalStateException("La configuración no se ha cargado");
        }
        return actual;
    }

    private static void leer(Path archivo, Properties destino) {
        try (InputStream entrada = Files.newInputStream(archivo)) {
            destino.load(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    public String getProtocolo() {
        return texto("servidor.protocolo");
    }

    public int getPuerto() {
        return entero("servidor.puerto");
    }

    public int getMaxSesiones() {
        return entero("servidor.maxSesiones");
    }

    /** @return tamaño máximo de una trama en bytes (por defecto 16 MiB). */
    public int getMaxTramaBytes() {
        return props.containsKey("servidor.maxTramaBytes")
                ? entero("servidor.maxTramaBytes")
                : MAX_TRAMA_BYTES_POR_DEFECTO;
    }

    /** @return {@code CONSOLA} o {@code ESCRITORIO}; la presentación lo traduce a su enum. */
    public String getTipoVista() {
        return texto("servidor.vista");
    }

    public int getHilosConsumidores() {
        return entero("cola.hilosConsumidores");
    }

    public String getMotorBD() {
        return texto("bd.motor");
    }

    public String getUrlBD() {
        return texto("bd.url");
    }

    public String getUsuarioBD() {
        return texto("bd.usuario");
    }

    public String getClaveBD() {
        return texto("bd.clave");
    }

    public int getPoolBD() {
        return entero("bd.pool");
    }

    public Path getArchivoUsuarios() {
        return Path.of(texto("usuarios.archivoInicial"));
    }

    public Path getDirectorioArchivos() {
        return Path.of(texto("archivos.directorio"));
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
}
