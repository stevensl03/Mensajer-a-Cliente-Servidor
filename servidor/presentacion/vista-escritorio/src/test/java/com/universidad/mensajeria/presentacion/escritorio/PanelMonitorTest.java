package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lógica de S1 · Monitor. JPanel, JTable y JList se pueden crear sin pantalla. */
class PanelMonitorTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 25, 10, 42, 5);
    private static final String HASH = "abcd" + "0".repeat(56) + "wxyz";

    @Test
    void tarjetaDeTextoAlForo() {
        String t = PanelMonitor.tarjeta(new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.FORO, "20201001", null, HORA,
                HASH, "Hola", 4, 1, null, List.of()));

        assertTrue(t.contains("10:42:05 · FORO · 20201001 → todos"), t);
        assertTrue(t.contains("TEXTO · 4 car · 1 pal"), t);
        assertTrue(t.contains("SHA-256 abcd…wxyz"), t);
        assertTrue(t.contains("#1E5AA8"), "FORO en azul");
    }

    @Test
    void tarjetaDeImagenPrivada() {
        var versiones = new ArrayList<ImagenVersionDTO>();
        for (TipoFiltro etapa : TipoFiltro.values()) {
            versiones.add(new ImagenVersionDTO(etapa.ordinal(), etapa, "f.png", "almacen/f.png", 1, "h"));
        }
        String t = PanelMonitor.tarjeta(new MensajeDTO("m2", TipoMensaje.IMAGEN, Canal.PRIVADO, "20201001",
                "20201002", HORA, HASH, null, 0, 0, "f.png", versiones));

        assertTrue(t.contains("PRIVADO · 20201001 → 20201002"), t);
        assertTrue(t.contains("IMAGEN · 5 filtros ✓"), t);
        assertTrue(t.contains("#2E7D32"), "PRIVADO en verde");
    }

    @Test
    void tarjetasDeEventos() {
        String login = PanelMonitor.tarjeta(evento(TipoEvento.LOGIN_OK), 3, 10).orElseThrow();
        assertTrue(login.contains("10:42:05 · LOGIN · 20201001 · 10.0.0.5"), login);
        assertTrue(login.contains("LOGIN_OK") && login.contains("Sesión 3 de 10"), login);

        String cupo = PanelMonitor.tarjeta(evento(TipoEvento.CUPO_LLENO), 10, 10).orElseThrow();
        assertTrue(cupo.contains("CUPO · 20201001 · 10.0.0.5"), cupo);
        assertTrue(cupo.contains("CUPO_LLENO (rechazado)") && cupo.contains("Sesiones 10 de 10"), cupo);
        assertTrue(cupo.contains("#C62828"), "CUPO_LLENO en rojo");

        assertTrue(PanelMonitor.tarjeta(evento(TipoEvento.CONEXION), 1, 10).isEmpty(), "los demás eventos no generan tarjeta");
    }

    @Test
    void escapaElHtmlDeLosDatosDelUsuario() {
        String t = PanelMonitor.tarjeta(new EventoLogDTO("e", TipoEvento.LOGIN_OK, "x", "<b>x</b>", null, HORA), 1, 1)
                .orElseThrow();
        assertTrue(t.contains("&lt;b&gt;x&lt;/b&gt;"), t);
        assertTrue(t.contains("—"), "ip nula se muestra como —");
    }

    @Test
    void textoDelCupo() {
        assertEquals("0 de 10 sesiones", PanelMonitor.textoCupo(List.of(), 10));
        var juan = sesion("s1", "Juan Martínez");
        assertEquals("1 de 10 sesiones", PanelMonitor.textoCupo(List.of(juan), 10));
        assertEquals("3 de 10 sesiones · Juan Martínez tiene 2 sesiones abiertas",
                PanelMonitor.textoCupo(List.of(juan, sesion("s2", "Juan Martínez"), sesion("s3", "Ana Pérez")), 10));
    }

    @Test
    void abreviarHash() {
        assertEquals("abcd…wxyz", PanelMonitor.abreviarHash(HASH));
        assertEquals("corto", PanelMonitor.abreviarHash("corto"));
        assertEquals("—", PanelMonitor.abreviarHash(null));
    }

    @Test
    void refrescarSesionesYMaximoDeTarjetas() {
        var panel = new PanelMonitor(new FachadaVacia(), 10);
        panel.refrescarSesiones(List.of(sesion("s1", "Ana Pérez"), sesion("s2", "Ana Pérez")));
        assertEquals(2, panel.filasSesiones());

        for (int i = 0; i < PanelMonitor.MAX_TARJETAS + 5; i++) {
            panel.agregarEvento(new EventoLogDTO("e" + i, TipoEvento.LOGIN_OK, "x", "u" + i, "ip", HORA), 1);
        }
        panel.agregarEvento(evento(TipoEvento.CONEXION), 1);

        List<String> tarjetas = panel.tarjetas();
        assertEquals(PanelMonitor.MAX_TARJETAS, tarjetas.size());
        assertTrue(tarjetas.get(0).contains("u" + (PanelMonitor.MAX_TARJETAS + 4)), "la más reciente arriba");
        assertFalse(tarjetas.stream().anyMatch(t -> t.contains(" u0 ")), "se descartan las más viejas");
    }

    @Test
    void textoDeLaBarraDeEstado() {
        var d = new VentanaPrincipal.Datos("TCP", 5000, "MYSQL", 10);
        assertEquals("Activo · Protocolo TCP · Puerto 5000 · IP 192.168.1.10 · Motor BD MYSQL · Sesiones 3 / 10",
                VentanaPrincipal.textoEstado(d, "192.168.1.10", 3));
    }

    private static EventoLogDTO evento(TipoEvento t) {
        return new EventoLogDTO("e1", t, t.name(), "20201001", "10.0.0.5", HORA);
    }

    private static SesionDTO sesion(String id, String nombre) {
        return new SesionDTO(id + "-0000", "c-" + id, nombre, "10.0.0.5", HORA);
    }

    /** Fachada sin comportamiento: el panel solo la usa al cerrar una sesión. */
    private static final class FachadaVacia implements IFachadaServidor {
        @Override public void iniciarServidor() { }
        @Override public void detenerServidor() { }
        @Override public void publicarEnForo(ContenidoDTO c) { }
        @Override public void enviarPrivado(String destino, ContenidoDTO c) { }
        @Override public String informe(TipoInforme t, Map<String, String> filtros) { return "{}"; }
        @Override public List<SesionDTO> listarSesiones() { return List.of(); }
        @Override public List<EstadoUsuarioDTO> listarUsuarios() { return List.of(); }
        @Override public void cerrarSesion(String idSesion) { }
        @Override public void suscribir(IObservadorServidor o) { }
        @Override public String getIpServidor() { return "127.0.0.1"; }
        @Override public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) { return Optional.empty(); }
    }
}
