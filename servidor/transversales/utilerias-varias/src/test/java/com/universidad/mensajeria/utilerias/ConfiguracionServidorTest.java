package com.universidad.mensajeria.utilerias;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionServidorTest {

    /** Los archivos reales del repositorio (el módulo se ejecuta desde transversales/utilerias-varias). */
    private static final Path CONFIG = Path.of("../../config");

    @TempDir
    Path dir;

    @Test
    void cargaLosArchivosDelRepositorio() {
        var cfg = ConfiguracionServidor.cargar(CONFIG.resolve("servidor.properties"), CONFIG.resolve("bd.properties"));

        assertEquals("TCP", cfg.protocolo());
        assertEquals(5000, cfg.puerto());
        assertEquals(10, cfg.maxSesiones());
        assertEquals(16_777_216, cfg.maxTramaBytes());
        assertEquals("CONSOLA", cfg.vista());
        assertEquals(256, cfg.colaSalida());
        assertEquals(1000, cfg.colaCapacidad());
        assertEquals(4, cfg.hilosConsumidores());
        assertEquals(4000, cfg.maxTextoCaracteres());
        assertEquals(5_242_880, cfg.maxImagenBytes());
        assertEquals(Path.of("./almacen"), cfg.directorioArchivos());
        assertEquals(Path.of("./config/usuarios.txt"), cfg.archivoUsuarios());
        assertEquals("MYSQL", cfg.bd().getProperty("bd.motor"));
        assertEquals("false", cfg.cache().getProperty("cache.l2.habilitada"));
        assertEquals("6379", cfg.cache().getProperty("redis.puerto"));
        assertFalse(cfg.cache().containsKey("servidor.puerto"));
        assertSame(cfg, ConfiguracionServidor.obtener());
    }

    @Test
    void fallaSiFaltaUnaClaveYLaNombra() throws IOException {
        Path servidor = copiarSin("servidor.properties", "cola.capacidad");
        Path bd = copiarSin("bd.properties", "bd.url");

        var e = assertThrows(IllegalStateException.class, () -> ConfiguracionServidor.cargar(servidor, bd));

        assertTrue(e.getMessage().contains("cola.capacidad (servidor.properties)"), e.getMessage());
        assertTrue(e.getMessage().contains("bd.url (bd.properties)"), e.getMessage());
    }

    @Test
    void fallaConNumerosInvalidos() throws IOException {
        Path bd = copiarSin("bd.properties", "");
        Path noEntero = conValor("servidor.maxSesiones", "diez");
        Path cero = conValor("cola.hilosConsumidores", "0");
        Path puerto = conValor("servidor.puerto", "70000");

        assertThrows(IllegalStateException.class, () -> ConfiguracionServidor.cargar(noEntero, bd));
        assertThrows(IllegalStateException.class, () -> ConfiguracionServidor.cargar(cero, bd));
        assertThrows(IllegalStateException.class, () -> ConfiguracionServidor.cargar(puerto, bd));
    }

    @Test
    void fallaSiNoExisteElArchivo() {
        assertThrows(UncheckedIOException.class,
                () -> ConfiguracionServidor.cargar(dir.resolve("no.properties"), CONFIG.resolve("bd.properties")));
    }

    private Path copiarSin(String archivo, String clave) throws IOException {
        String contenido = Files.readAllLines(CONFIG.resolve(archivo), StandardCharsets.UTF_8).stream()
                .filter(l -> clave.isEmpty() || !l.startsWith(clave + "="))
                .collect(Collectors.joining("\n"));
        return Files.writeString(dir.resolve(archivo), contenido, StandardCharsets.UTF_8);
    }

    private Path conValor(String clave, String valor) throws IOException {
        String contenido = Files.readAllLines(CONFIG.resolve("servidor.properties"), StandardCharsets.UTF_8).stream()
                .map(l -> l.startsWith(clave + "=") ? clave + "=" + valor : l)
                .collect(Collectors.joining("\n"));
        return Files.writeString(dir.resolve(clave + ".properties"), contenido, StandardCharsets.UTF_8);
    }
}
