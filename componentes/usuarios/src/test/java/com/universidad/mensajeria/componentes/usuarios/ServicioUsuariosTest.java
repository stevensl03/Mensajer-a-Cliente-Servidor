package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;
import com.universidad.mensajeria.persistencia.RepositorioUsuariosMemoria;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioUsuariosTest {

    private static final String ARCHIVO = """
            codigo;nombres;apellidos;contrasena;programa;rol
            # comentario
            20201001;Ana;Pérez;clave123;Ingeniería de Sistemas;USUARIO

            ADMIN01;Servidor;Administrador;admin2026;Administración;admin
            20201003;Sin;Campos;clave;Programa
            20201004;Rol;Malo;clave;Programa;JEFE
            """;

    private final GeneradorHashSHA256 hash = new GeneradorHashSHA256();
    private final IRepositorioUsuarios repositorio = new RepositorioUsuariosMemoria();
    private final ServicioUsuarios servicio = new ServicioUsuarios(repositorio, hash);
    private Path archivo;

    @BeforeEach
    void importar(@TempDir Path dir) throws IOException {
        archivo = Files.writeString(dir.resolve("usuarios.txt"), ARCHIVO, StandardCharsets.UTF_8);
        new ImportadorUsuarios(archivo, repositorio, hash).importarSiVacio();
    }

    @Test
    void importaSoloLasLineasValidas() {
        var registrados = servicio.listarRegistrados();
        assertEquals(2, registrados.size());
        assertEquals("Ingeniería de Sistemas", servicio.buscar("20201001").orElseThrow().programa());
        assertEquals(Rol.ADMIN, servicio.buscar("ADMIN01").orElseThrow().rol());
    }

    @Test
    void guardaElHashYNoLaClave() {
        String guardado = repositorio.buscarPorCodigo("20201001").orElseThrow().contrasenaHash();
        assertEquals(64, guardado.length());
        assertNotEquals("clave123", guardado);
    }

    @Test
    void autentica() {
        assertEquals("Ana", servicio.autenticar("20201001", "clave123").orElseThrow().nombres());
        assertTrue(servicio.autenticar("20201001", "otra").isEmpty());
        assertTrue(servicio.autenticar("noexiste", "clave123").isEmpty());
        assertTrue(servicio.autenticar(null, null).isEmpty());
    }

    @Test
    void identificaAlAdministrador() {
        assertTrue(servicio.esAdmin("ADMIN01"));
        assertFalse(servicio.esAdmin("20201001"));
        assertFalse(servicio.esAdmin("noexiste"));
    }

    @Test
    void noReimportaSiYaHayUsuarios() throws IOException {
        Files.writeString(archivo, "20209999;Otro;Usuario;x;Programa;USUARIO\n", StandardCharsets.UTF_8);
        new ImportadorUsuarios(archivo, repositorio, hash).importarSiVacio();
        assertEquals(2, servicio.listarRegistrados().size());
    }
}
