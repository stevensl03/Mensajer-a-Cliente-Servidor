package com.universidad.mensajeria.persistencia;

import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link IAlmacenamientoArchivos} sobre un directorio local ({@code archivos.directorio}).
 */
public final class AlmacenamientoDirectorioLocal implements IAlmacenamientoArchivos {

    private final Path raiz;

    public AlmacenamientoDirectorioLocal(Path raiz) {
        this.raiz = Objects.requireNonNull(raiz, "raiz");
    }

    @Override
    public Path guardar(String subdir, String nombre, byte[] datos) {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public byte[] leer(String ruta) {
        throw new UnsupportedOperationException("Fase 2");
    }
}
