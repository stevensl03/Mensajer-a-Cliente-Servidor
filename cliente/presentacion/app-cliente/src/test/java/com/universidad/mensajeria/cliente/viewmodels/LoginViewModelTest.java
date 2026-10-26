package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sin JavaFX: los ejecutores son directos (D-10). */
class LoginViewModelTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);

    private FachadaFalsa fachada;
    private AtomicInteger navegaciones;
    private LoginViewModel vm;

    @BeforeEach
    void preparar() {
        fachada = new FachadaFalsa();
        navegaciones = new AtomicInteger();
        vm = new LoginViewModel(fachada, Runnable::run, Runnable::run, "127.0.0.1:5000", List.of("TCP"),
                navegaciones::incrementAndGet);
    }

    @Test
    void elServidorSePrecargaYSoloSePuedeIngresarConCodigoYClave() {
        assertEquals("127.0.0.1:5000", vm.servidorProperty().get());
        assertEquals(List.of("TCP"), vm.protocolos());
        assertFalse(vm.puedeIngresarProperty().get());

        vm.codigoProperty().set("20201001");
        assertFalse(vm.puedeIngresarProperty().get());
        vm.claveProperty().set("clave123");
        assertTrue(vm.puedeIngresarProperty().get());

        vm.ocupadoProperty().set(true);
        assertFalse(vm.puedeIngresarProperty().get());
    }

    @Test
    void unLoginCorrectoNavegaYBorraLaClave() throws Exception {
        fachada.login = (c, k) -> CompletableFuture.completedFuture(ANA);
        llenar(" 20201001 ", "clave123", " 10.0.0.7 : 5001 ");

        assertTrue(vm.ingresar().get(5, TimeUnit.SECONDS));

        assertEquals(List.of("conectar 10.0.0.7:5001", "login 20201001 clave123"), fachada.llamadas);
        assertEquals(1, navegaciones.get());
        assertEquals("", vm.claveProperty().get(), "la clave no queda en memoria (R-85)");
        assertEquals("", vm.errorProperty().get());
        assertFalse(vm.ocupadoProperty().get());
    }

    @Test
    void traduceLosRechazosDelServidor() throws Exception {
        fachada.login = (c, k) -> CompletableFuture.failedFuture(
                new ExcepcionServidor(CodigoError.CREDENCIALES_INVALIDAS, "Código o contraseña incorrectos"));
        llenar("20201001", "mala", "127.0.0.1:5000");
        assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS));
        assertEquals(LoginViewModel.CREDENCIALES, vm.errorProperty().get());
        assertEquals("", vm.claveProperty().get());

        fachada.login = (c, k) -> CompletableFuture.failedFuture(
                new ExcepcionServidor(CodigoError.CUPO_LLENO, "No hay cupos libres en el servidor"));
        vm.claveProperty().set("clave123");
        assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS));
        assertEquals(LoginViewModel.CUPO, vm.errorProperty().get());
        assertEquals(0, navegaciones.get());
    }

    @Test
    void unErrorDeRedNombraElServidor() throws Exception {
        fachada.errorConectar = new UncheckedIOException("No fue posible conectar", new IOException("refused"));
        llenar("20201001", "clave123", "servidor.local:5000");

        assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS));

        assertEquals("No fue posible conectar con servidor.local:5000.", vm.errorProperty().get());
        assertEquals(List.of("conectar servidor.local:5000"), fachada.llamadas, "sin conexión no se intenta el login");
        assertFalse(vm.ocupadoProperty().get());
    }

    @Test
    void unTiempoAgotadoTambienEsErrorDeConexion() throws Exception {
        fachada.login = (c, k) -> CompletableFuture.failedFuture(new TimeoutException());
        llenar("20201001", "clave123", "127.0.0.1:5000");

        assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS));

        assertEquals("No fue posible conectar con 127.0.0.1:5000.", vm.errorProperty().get());
    }

    @Test
    void unServidorMalEscritoNoLlegaALaFachada() throws Exception {
        vm.codigoProperty().set("20201001");
        vm.claveProperty().set("clave123");
        for (String malo : List.of("127.0.0.1", ":5000", "host:", "host:abc", "host:0", "host:70000")) {
            vm.servidorProperty().set(malo);

            assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS), malo);
            assertEquals(LoginViewModel.SERVIDOR_INVALIDO, vm.errorProperty().get(), malo);
        }
        assertTrue(fachada.llamadas.isEmpty());
        assertEquals("clave123", vm.claveProperty().get(), "un error de formato no borra la clave");
    }

    @Test
    void elErrorAnteriorSeBorraAlReintentar() throws Exception {
        vm.errorProperty().set("viejo");
        var pendiente = new CompletableFuture<UsuarioDTO>();
        fachada.login = (c, k) -> pendiente;
        llenar("20201001", "clave123", "127.0.0.1:5000");

        var resultado = vm.ingresar();

        assertEquals("", vm.errorProperty().get());
        assertTrue(vm.ocupadoProperty().get(), "mientras espera, ocupado");
        assertFalse(vm.ingresar().get(5, TimeUnit.SECONDS), "un segundo clic mientras espera se ignora");
        pendiente.complete(ANA);
        assertTrue(resultado.get(5, TimeUnit.SECONDS));
        assertEquals(1, fachada.llamadas.stream().filter(l -> l.startsWith("login")).count());
    }

    private void llenar(String codigo, String clave, String servidor) {
        vm.codigoProperty().set(codigo);
        vm.claveProperty().set(clave);
        vm.servidorProperty().set(servidor);
    }
}
