package com.universidad.mensajeria.presentacion.consola;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El menú S4 con entrada simulada: cada prueba termina con «0» y «s». */
class VistaConsolaTest {

    private final FachadaGrabadora fachada = new FachadaGrabadora();
    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();

    @Test
    void lasOpcionesDeConsultaLlamanALaFachada() {
        ejecutar("1", "2", "3", "4", "m", "0", "s");

        assertEquals(List.of("sesiones", "informe:USUARIOS_REGISTRADOS", "informe:USUARIOS_CONECTADOS",
                "informe:MENSAJES", "detener"), fachada.llamadas);
        String texto = texto();
        assertTrue(texto.contains("Mensajería Académica Unillanos · Servidor v1.0 · IP 192.168.1.10"));
        assertTrue(texto.contains("1   abcd   20201001   Ana Pérez"), texto);
        assertTrue(texto.contains("2 sesiones activas"));
        assertTrue(texto.contains("\"tipo\": \"USUARIOS_REGISTRADOS\""), "el informe sale con sangría");
        assertTrue(texto.contains("Servidor detenido."));
    }

    @Test
    void elInformeDeLogsPideFiltrosOpcionales() {
        ejecutar("4", "l", "login_ok", "20201001", "4", "l", "", "", "0", "s");

        assertEquals(List.of("informe:LOGS:{tipoEvento=LOGIN_OK, usuario=20201001}", "informe:LOGS", "detener"),
                fachada.llamadas);
    }

    @Test
    void elAdminEscribeEnElForoYEnPrivado() {
        ejecutar("5", "Hola a todos", "6", "20201002", "Hola Luis", "0", "s");

        assertEquals(List.of("foro:Hola a todos", "privado:20201002:Hola Luis", "detener"), fachada.llamadas);
    }

    @Test
    void cerrarUnaSesionPorSuNumero() {
        ejecutar("7", "2", "7", "9", "0", "s");

        assertEquals(List.of("sesiones", "cerrar:efgh-2222", "sesiones", "detener"), fachada.llamadas);
        assertTrue(texto().contains("Sesión de 20201002 cerrada."));
        assertTrue(texto().contains("Opción no válida"), "el número 9 no existe");
    }

    @Test
    void unaOpcionInvalidaVuelveAMostrarElMenu() {
        ejecutar("8", "hola", "0", "n", "0", "s");

        assertEquals(List.of("detener"), fachada.llamadas, "con «n» no se detiene");
        String texto = texto();
        assertEquals(2, texto.split("Opción no válida", -1).length - 1);
        assertTrue(texto.split("0\\) Detener servidor", -1).length - 1 >= 4);
    }

    @Test
    void unErrorDeLaFachadaSeMuestraYElMenuSigue() {
        fachada.fallo = new IllegalStateException("El destinatario no existe: 999");

        ejecutar("6", "999", "Hola", "0", "s");

        assertTrue(texto().contains("Error: El destinatario no existe: 999"));
        assertEquals(List.of("detener"), fachada.llamadas);
    }

    @Test
    void sinEntradaElServidorSigueHastaDetener() throws Exception {
        var vista = new VistaConsola(fachada, new ByteArrayInputStream(new byte[0]), new PrintStream(salida, true));
        var hilo = Thread.ofVirtual().start(vista::iniciar);

        Thread.sleep(100);
        assertTrue(hilo.isAlive(), "con la entrada cerrada la vista espera, no detiene el servidor");
        vista.detener();

        assertTrue(hilo.join(Duration.ofSeconds(2)));
        assertTrue(texto().contains("Entrada cerrada"));
        assertTrue(fachada.llamadas.isEmpty());
    }

    @Test
    void onMensajeImprimeElDetalleDeCadaMensaje() {
        var vista = new VistaConsola(fachada, new ByteArrayInputStream(new byte[0]), new PrintStream(salida, true));
        LocalDateTime t = LocalDateTime.of(2026, 10, 13, 10, 42, 5);

        vista.onMensaje(new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.FORO, "20201001", null, t, "abc123",
                "Hola a todos", 12, 3, null, List.of()));
        vista.onMensaje(new MensajeDTO("m2", TipoMensaje.IMAGEN, Canal.PRIVADO, "20201001", "20201002", t, "def456",
                null, 0, 0, "foto.png", List.of(new ImagenVersionDTO(0, TipoFiltro.ORIGINAL, "foto.png", "/a", 1, "h"))));

        String texto = texto();
        assertTrue(texto.contains("[MENSAJE 10:42:05] FORO · 20201001 → todos"));
        assertTrue(texto.contains("TEXTO \"Hola a todos\" · 12 car · 3 pal"));
        assertTrue(texto.contains("SHA-256 abc123"));
        assertTrue(texto.contains("PRIVADO · 20201001 → 20201002"));
        assertTrue(texto.contains("IMAGEN foto.png"));
    }

    private void ejecutar(String... lineas) {
        String entrada = String.join("\n", lineas) + "\n";
        var vista = new VistaConsola(fachada, new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(salida, true, StandardCharsets.UTF_8));
        assertTimeoutPreemptively(Duration.ofSeconds(5), vista::iniciar);
    }

    private String texto() {
        return salida.toString(StandardCharsets.UTF_8);
    }
}
