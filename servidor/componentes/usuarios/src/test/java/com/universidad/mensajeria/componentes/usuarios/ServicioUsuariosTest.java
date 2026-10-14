package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioUsuariosTest {

    private final GeneradorHashSHA256 hash = new GeneradorHashSHA256();
    private final UsuarioRegistroDTO ana = registro("20201001", "Ana", "clave123", Rol.USUARIO);
    private final UsuarioRegistroDTO admin = registro("ADMIN01", "Servidor", "admin2026", Rol.ADMIN);
    private final IUsuariosDisponibles servicio = FabricaUsuarios.crear(List.of(ana, admin), hash);

    @Test
    void autenticacionCorrecta() {
        UsuarioDTO u = servicio.autenticar("20201001", "clave123").orElseThrow();
        assertEquals(new UsuarioDTO("20201001", "Ana", "Apellido", "Programa", Rol.USUARIO), u);
        assertEquals("ADMIN01", servicio.autenticar("ADMIN01", "admin2026").orElseThrow().codigo());
    }

    @Test
    void autenticacionIncorrecta() {
        assertTrue(servicio.autenticar("20201001", "otra").isEmpty(), "clave equivocada");
        assertTrue(servicio.autenticar("20201001", "CLAVE123").isEmpty(), "distingue mayúsculas");
        assertTrue(servicio.autenticar("20201001", "").isEmpty(), "clave vacía");
        assertTrue(servicio.autenticar("noexiste", "clave123").isEmpty(), "usuario inexistente");
        assertTrue(servicio.autenticar("ADMIN01", "clave123").isEmpty(), "clave de otro usuario");
        assertTrue(servicio.autenticar(null, "clave123").isEmpty());
        assertTrue(servicio.autenticar("20201001", null).isEmpty());
    }

    @Test
    void aceptaHashesGuardadosEnMayusculas() {
        var mayus = new UsuarioRegistroDTO("20201009", "Val", "Ramírez",
                hash.generar("x".getBytes(StandardCharsets.UTF_8)).toUpperCase(), "Programa", Rol.USUARIO);
        var s = FabricaUsuarios.crear(List.of(mayus, admin), hash);
        assertTrue(s.autenticar("20201009", "x").isPresent());
    }

    @Test
    void administrador() {
        assertEquals("ADMIN01", servicio.administrador().codigo());
        assertEquals(Rol.ADMIN, servicio.administrador().rol());
    }

    @Test
    void existeYBuscar() {
        assertTrue(servicio.existe("20201001"));
        assertTrue(servicio.existe("ADMIN01"));
        assertFalse(servicio.existe("noexiste"));
        assertFalse(servicio.existe(null));
        assertEquals("Programa", servicio.buscar("20201001").orElseThrow().programa());
        assertEquals(Optional.empty(), servicio.buscar("noexiste"));
        assertEquals(Optional.empty(), servicio.buscar(null));
    }

    @Test
    void listaTodosEnElOrdenRecibido() {
        assertEquals(List.of("20201001", "ADMIN01"),
                servicio.listarRegistrados().stream().map(UsuarioDTO::codigo).toList());
        assertThrows(UnsupportedOperationException.class, () -> servicio.listarRegistrados().clear());
    }

    @Test
    void nuncaExponeElHashDeLaContrasena() throws Exception {
        // Ningún método público devuelve UsuarioRegistroDTO...
        for (Method m : IUsuariosDisponibles.class.getMethods()) {
            assertFalse(m.getGenericReturnType().getTypeName().contains("UsuarioRegistroDTO"), m.getName());
        }
        // ...UsuarioDTO no tiene un campo para él...
        assertFalse(Arrays.stream(UsuarioDTO.class.getRecordComponents())
                .map(RecordComponent::getName).anyMatch(n -> n.toLowerCase().contains("contrasena")));
        // ...y no aparece al serializar lo que devuelve el componente.
        String json = new SerializadorJSON().aJson(List.of(servicio.listarRegistrados(),
                servicio.autenticar("20201001", "clave123").orElseThrow(), servicio.administrador()));
        assertFalse(json.contains(ana.contrasenaHash()));
        assertFalse(json.contains(admin.contrasenaHash()));
    }

    @Test
    void elCatalogoNoCambiaSiCambiaLaListaOriginal() {
        var lista = new ArrayList<>(List.of(ana, admin));
        var s = FabricaUsuarios.crear(lista, hash);
        lista.clear();
        assertEquals(2, s.listarRegistrados().size());
    }

    @Test
    void rechazaListasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> FabricaUsuarios.crear(List.of(ana), hash), "sin ADMIN");
        var otroAdmin = registro("ADMIN02", "Otro", "x", Rol.ADMIN);
        assertThrows(IllegalArgumentException.class,
                () -> FabricaUsuarios.crear(List.of(ana, admin, otroAdmin), hash), "dos ADMIN");
        assertThrows(IllegalArgumentException.class,
                () -> FabricaUsuarios.crear(List.of(ana, admin, ana), hash), "código repetido");
    }

    private UsuarioRegistroDTO registro(String codigo, String nombres, String clave, Rol rol) {
        return new UsuarioRegistroDTO(codigo, nombres, "Apellido",
                hash.generar(clave.getBytes(StandardCharsets.UTF_8)), "Programa", rol);
    }
}
