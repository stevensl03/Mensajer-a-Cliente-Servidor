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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportadorUsuariosTest {

    private static final String ENCABEZADO = "codigo;nombres;apellidos;contrasena;programa;rol\n";
    private static final String ADMIN = "ADMIN01;Servidor;Administrador;admin2026;Administración;admin\n";

    private final GeneradorHashSHA256 hash = new GeneradorHashSHA256();

    @TempDir
    Path dir;

    @Test
    void leeLasLineasValidasYGuardaElHash() throws IOException {
        List<UsuarioRegistroDTO> usuarios = leer(ENCABEZADO + "# comentario\n"
                + "20201001;Ana;Pérez;clave123;Ingeniería de Sistemas;USUARIO\n\n" + ADMIN);

        assertEquals(2, usuarios.size());
        assertEquals("Ingeniería de Sistemas", usuarios.get(0).programa());
        assertEquals(Rol.ADMIN, usuarios.get(1).rol());
        assertEquals(hash.generar("clave123".getBytes(StandardCharsets.UTF_8)), usuarios.get(0).contrasenaHash());
    }

    @Test
    void unaLineaMalFormadaDetieneLaCargaYNoMuestraLaClave() {
        var e = assertThrows(IllegalStateException.class,
                () -> leer(ENCABEZADO + ADMIN + "20201003;Sin;Campos;secreta99;Programa\n"));

        assertTrue(e.getMessage().contains("línea 3"), e.getMessage());
        assertFalse(e.getMessage().contains("secreta99"));
    }

    @Test
    void rolDesconocidoYCodigoRepetido() {
        var rol = assertThrows(IllegalStateException.class,
                () -> leer(ENCABEZADO + ADMIN + "20201004;Rol;Malo;clave;Programa;JEFE\n"));
        assertTrue(rol.getMessage().contains("rol desconocido"), rol.getMessage());

        var repetido = assertThrows(IllegalStateException.class,
                () -> leer(ENCABEZADO + ADMIN + "ADMIN01;Otro;Admin;x;Programa;USUARIO\n"));
        assertTrue(repetido.getMessage().contains("código repetido ADMIN01"), repetido.getMessage());
    }

    @Test
    void exigeExactamenteUnAdmin() {
        assertThrows(IllegalStateException.class, () -> leer(ENCABEZADO + "20201001;Ana;Pérez;c;P;USUARIO\n"));
        assertThrows(IllegalStateException.class,
                () -> leer(ENCABEZADO + ADMIN + "ADMIN02;Otro;Admin;x;Programa;ADMIN\n"));
    }

    @Test
    void exigeElEncabezado() {
        assertThrows(IllegalStateException.class, () -> leer(ADMIN));
    }

    @Test
    void elArchivoDelRepositorioEsValido() {
        List<UsuarioRegistroDTO> usuarios = new ImportadorUsuarios(Path.of("../../config/usuarios.txt"), hash).leer();

        assertEquals(10, usuarios.size());
        assertEquals(1, usuarios.stream().filter(u -> u.rol() == Rol.ADMIN).count());
    }

    private List<UsuarioRegistroDTO> leer(String contenido) throws IOException {
        Path archivo = Files.writeString(dir.resolve("usuarios.txt"), contenido, StandardCharsets.UTF_8);
        return new ImportadorUsuarios(archivo, hash).leer();
    }
}
