package com.universidad.mensajeria.persistencia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenamientoDirectorioLocalTest {

    @TempDir
    Path raiz;

    @Test
    void guardaEnElSubdirectorioYLeeLoMismo() {
        var almacen = new AlmacenamientoDirectorioLocal(raiz);
        byte[] datos = "Hola, ñandú".getBytes(StandardCharsets.UTF_8);

        Path ruta = almacen.guardar("textos", "m1.txt", datos);

        assertEquals(raiz.resolve("textos").resolve("m1.txt"), ruta);
        assertArrayEquals(datos, almacen.leer(ruta.toString()));
    }

    @Test
    void sobrescribeSinDejarTemporales() throws Exception {
        var almacen = new AlmacenamientoDirectorioLocal(raiz);
        almacen.guardar("filtradas", "m1_1_SEPIA.png", new byte[]{1});
        Path ruta = almacen.guardar("filtradas", "m1_1_SEPIA.png", new byte[]{2, 3});

        assertArrayEquals(new byte[]{2, 3}, almacen.leer(ruta.toString()));
        try (var archivos = Files.list(raiz.resolve("filtradas"))) {
            assertEquals(1, archivos.count());
        }
    }

    @Test
    void borrarEsIdempotente() {
        var almacen = new AlmacenamientoDirectorioLocal(raiz);
        Path ruta = almacen.guardar("originales", "m1_foto.png", new byte[]{1});

        almacen.borrar(ruta.toString());

        assertFalse(Files.exists(ruta));
        assertDoesNotThrow(() -> almacen.borrar(ruta.toString()));
    }

    @Test
    void rechazaNombresQueSalenDeLaRaiz() {
        var almacen = new AlmacenamientoDirectorioLocal(raiz);

        assertThrows(IllegalArgumentException.class, () -> almacen.guardar("textos", "../fuera.txt", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> almacen.guardar("..", "x.txt", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> almacen.guardar("textos", "a/b.txt", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> almacen.leer(raiz.resolve("../otro.txt").toString()));
        assertTrue(Files.notExists(raiz.resolve("textos")));
    }

    @Test
    void leerUnArchivoInexistente() {
        var almacen = new AlmacenamientoDirectorioLocal(raiz);
        assertThrows(UncheckedIOException.class, () -> almacen.leer(raiz.resolve("textos/no.txt").toString()));
    }
}
