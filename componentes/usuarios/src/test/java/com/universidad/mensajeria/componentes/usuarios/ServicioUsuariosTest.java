package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioUsuariosTest {

    private final GeneradorHashSHA256 hash = new GeneradorHashSHA256();
    private final IUsuariosDisponibles servicio = FabricaUsuarios.crear(List.of(
            registro("20201001", "Ana", "clave123", Rol.USUARIO),
            registro("ADMIN01", "Servidor", "admin2026", Rol.ADMIN)), hash);

    @Test
    void autentica() {
        assertEquals("Ana", servicio.autenticar("20201001", "clave123").orElseThrow().nombres());
        assertTrue(servicio.autenticar("20201001", "otra").isEmpty());
        assertTrue(servicio.autenticar("noexiste", "clave123").isEmpty());
        assertTrue(servicio.autenticar(null, null).isEmpty());
    }

    @Test
    void listaYBuscaLosRegistrados() {
        assertEquals(2, servicio.listarRegistrados().size());
        assertEquals("Programa", servicio.buscar("20201001").orElseThrow().programa());
        assertTrue(servicio.existe("20201001"));
        assertFalse(servicio.existe("noexiste"));
    }

    @Test
    void identificaAlAdministrador() {
        assertEquals("ADMIN01", servicio.administrador().codigo());
    }

    private UsuarioRegistroDTO registro(String codigo, String nombres, String clave, Rol rol) {
        return new UsuarioRegistroDTO(codigo, nombres, "Apellido",
                hash.generar(clave.getBytes(StandardCharsets.UTF_8)), "Programa", rol);
    }
}
