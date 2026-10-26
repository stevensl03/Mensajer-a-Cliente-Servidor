package com.universidad.mensajeria.cliente.utilerias;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionClienteTest {

    /** El archivo real del repositorio (el módulo se ejecuta desde transversales/utilerias-cliente). */
    private static final Path CONFIG = Path.of("../../config/cliente.properties");

    @TempDir
    Path dir;

    @Test
    void cargaElArchivoDelRepositorio() {
        var cfg = ConfiguracionCliente.cargar(CONFIG);

        assertEquals("127.0.0.1", cfg.host());
        assertEquals(5000, cfg.puerto());
        assertEquals("TCP", cfg.protocolo());
        assertEquals(16_777_216, cfg.maxTramaBytes());
        assertEquals(15, cfg.timeoutSegundos());
        assertEquals("jdbc:h2:./data/cliente_{codigo}", cfg.urlH2());
        assertEquals("sa", cfg.usuarioH2());
        assertEquals("", cfg.claveH2());
        assertEquals(500, cfg.cacheMax());
    }

    @Test
    void fallaSiFaltanClavesYLasNombraTodas() throws IOException {
        Path archivo = copiarSin("servidor.host", "h2.clave");

        var e = assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(archivo));

        assertTrue(e.getMessage().contains("servidor.host (cliente.properties)"), e.getMessage());
        assertTrue(e.getMessage().contains("h2.clave (cliente.properties)"), e.getMessage());
    }

    @Test
    void fallaConNumerosInvalidos() throws IOException {
        assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(conValor("servidor.puerto", "cinco")));
        assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(conValor("servidor.puerto", "0")));
        assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(conValor("servidor.puerto", "70000")));
        assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(conValor("peticion.timeoutSegundos", "0")));
        assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(conValor("cache.mensajes.max", "-1")));
    }

    @Test
    void exigeElMarcadorDelCodigoEnLaUrlDeH2() throws IOException {
        Path archivo = conValor("h2.url", "jdbc:h2:./data/compartida");

        var e = assertThrows(IllegalStateException.class, () -> ConfiguracionCliente.cargar(archivo));

        assertTrue(e.getMessage().contains("{codigo}"), e.getMessage());
    }

    @Test
    void fallaSiNoExisteElArchivo() {
        assertThrows(UncheckedIOException.class, () -> ConfiguracionCliente.cargar(dir.resolve("no.properties")));
    }

    private Path copiarSin(String... claves) throws IOException {
        String contenido = Files.readAllLines(CONFIG, StandardCharsets.UTF_8).stream()
                .filter(l -> java.util.Arrays.stream(claves).noneMatch(c -> l.startsWith(c + "=")))
                .collect(Collectors.joining("\n"));
        return escribir(contenido);
    }

    private Path conValor(String clave, String valor) throws IOException {
        String contenido = Files.readAllLines(CONFIG, StandardCharsets.UTF_8).stream()
                .map(l -> l.startsWith(clave + "=") ? clave + "=" + valor : l)
                .collect(Collectors.joining("\n"));
        return escribir(contenido);
    }

    private Path escribir(String contenido) throws IOException {
        Path archivo = dir.resolve("cliente.properties");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }
}
