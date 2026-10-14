package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportadorUsuariosTest {

    private static final String ARCHIVO = """
            codigo;nombres;apellidos;contrasena;programa;rol
            # comentario
            20201001;Ana;Pérez;clave123;Ingeniería de Sistemas;USUARIO

            ADMIN01;Servidor;Administrador;admin2026;Administración;admin
            20201003;Sin;Campos;clave;Programa
            20201004;Rol;Malo;clave;Programa;JEFE
            """;

    @TempDir
    Path dir;

    @Test
    void leeSoloLasLineasValidasYGuardaElHash() throws IOException {
        Path archivo = Files.writeString(dir.resolve("usuarios.txt"), ARCHIVO, StandardCharsets.UTF_8);

        List<UsuarioRegistroDTO> usuarios = new ImportadorUsuarios(archivo, new GeneradorHashSHA256()).leer();

        assertEquals(2, usuarios.size());
        assertEquals("Ingeniería de Sistemas", usuarios.get(0).programa());
        assertEquals(Rol.ADMIN, usuarios.get(1).rol());
        assertEquals(64, usuarios.get(0).contrasenaHash().length());
        assertNotEquals("clave123", usuarios.get(0).contrasenaHash());
    }
}
