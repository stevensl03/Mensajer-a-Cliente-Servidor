package com.universidad.mensajeria.cliente.persistencia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProveedorConexionH2Test {

    @TempDir
    Path dir;

    private ProveedorConexionH2 proveedor;

    @AfterEach
    void cerrar() {
        if (proveedor != null) {
            proveedor.cerrar();
        }
    }

    @Test
    void abrirCreaLasTresTablas() throws SQLException {
        proveedor = enMemoria();
        proveedor.abrir("20201001");

        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                     + "WHERE TABLE_NAME IN ('SESION', 'MENSAJE', 'ADJUNTO')")) {
            rs.next();
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    void unaBasePorUsuarioEnArchivoQueSeConservaAlReabrir() throws SQLException {
        proveedor = new ProveedorConexionH2("jdbc:h2:" + dir.toAbsolutePath() + "/cliente_{codigo}", "sa", "");
        proveedor.abrir("20201001");
        ejecutar("INSERT INTO SESION (CODIGO_USUARIO, IP_SERVIDOR, PUERTO, INICIO) "
                + "VALUES ('20201001', '127.0.0.1', 5000, CURRENT_TIMESTAMP)");

        proveedor.abrir("20201002");
        assertEquals(0, sesiones());
        proveedor.abrir("20201001");

        assertEquals(1, sesiones(), "el esquema idempotente no debe borrar datos");
        assertTrue(Files.exists(dir.resolve("cliente_20201001.mv.db")));
        assertTrue(Files.exists(dir.resolve("cliente_20201002.mv.db")));
    }

    @Test
    void abrirDosVecesElMismoUsuarioNoHaceNada() throws SQLException {
        proveedor = enMemoria();
        proveedor.abrir("20201001");
        ejecutar("INSERT INTO SESION (CODIGO_USUARIO, IP_SERVIDOR, PUERTO, INICIO) "
                + "VALUES ('20201001', '127.0.0.1', 5000, CURRENT_TIMESTAMP)");

        proveedor.abrir("20201001");

        assertEquals(1, sesiones());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../otro", "a/b", "a\\b", "a;INIT=RUNSCRIPT", "a b", "123456789012345678901"})
    void rechazaCodigosQueNoSonSeguros(String codigo) {
        proveedor = enMemoria();

        assertThrows(IllegalArgumentException.class, () -> proveedor.abrir(codigo));
    }

    @Test
    void sinAbrirNoEntregaConexionesYCerrarEsIdempotente() {
        proveedor = enMemoria();

        assertThrows(IllegalStateException.class, () -> proveedor.obtenerConexion());
        proveedor.cerrar();
        proveedor.abrir("x");
        proveedor.cerrar();
        proveedor.cerrar();
        assertThrows(IllegalStateException.class, () -> proveedor.obtenerConexion());
    }

    @Test
    void exigeElMarcadorEnLaUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ProveedorConexionH2("jdbc:h2:mem:fija", "sa", ""));
    }

    private ProveedorConexionH2 enMemoria() {
        return new ProveedorConexionH2("jdbc:h2:mem:" + UUID.randomUUID() + "_{codigo}", "sa", "");
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private int sesiones() throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM SESION")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
