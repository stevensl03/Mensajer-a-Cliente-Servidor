package com.universidad.mensajeria.persistencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@link IAlmacenamientoArchivos} sobre un directorio local ({@code archivos.directorio}).
 *
 * <p>Cada archivo se escribe primero en un temporal y luego se mueve, así
 * nunca queda un archivo a medias con su nombre definitivo. Los nombres no
 * pueden salir de la raíz.</p>
 */
public final class AlmacenamientoDirectorioLocal implements IAlmacenamientoArchivos {

    private static final Pattern SEGMENTO_VALIDO = Pattern.compile("[^/\\\\:*?\"<>|]+");

    private final Path raiz;

    /**
     * @param raiz directorio raíz; se crea si no existe al guardar el primer archivo.
     */
    public AlmacenamientoDirectorioLocal(Path raiz) {
        this.raiz = Objects.requireNonNull(raiz, "raiz").normalize();
    }

    @Override
    public Path guardar(String subdir, String nombre, byte[] datos) {
        Objects.requireNonNull(datos, "datos");
        Path destino = raiz.resolve(validar(subdir, "subdir")).resolve(validar(nombre, "nombre"));
        try {
            Files.createDirectories(destino.getParent());
            Path temporal = Files.createTempFile(destino.getParent(), ".tmp-", ".part");
            try {
                Files.write(temporal, datos);
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporal);
            }
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + destino, e);
        }
    }

    @Override
    public byte[] leer(String ruta) {
        Path archivo = dentroDeLaRaiz(ruta);
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    @Override
    public void borrar(String ruta) {
        Path archivo = dentroDeLaRaiz(ruta);
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar " + archivo, e);
        }
    }

    private static String validar(String segmento, String campo) {
        Objects.requireNonNull(segmento, campo);
        if (!SEGMENTO_VALIDO.matcher(segmento).matches() || segmento.equals(".") || segmento.equals("..")) {
            throw new IllegalArgumentException(campo + " inválido: " + segmento);
        }
        return segmento;
    }

    private Path dentroDeLaRaiz(String ruta) {
        Path archivo = Path.of(Objects.requireNonNull(ruta, "ruta")).normalize();
        if (!archivo.toAbsolutePath().startsWith(raiz.toAbsolutePath())) {
            throw new IllegalArgumentException("La ruta está fuera del directorio de archivos: " + ruta);
        }
        return archivo;
    }
}
