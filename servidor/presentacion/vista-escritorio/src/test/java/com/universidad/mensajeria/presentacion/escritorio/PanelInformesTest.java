package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lógica de S3 · Informes. Los componentes Swing se pueden crear sin pantalla. */
class PanelInformesTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 25, 9, 30, 15, 123_000_000);
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final String HASH = "abcd" + "0".repeat(56) + "wxyz";

    private final SerializadorJSON json = new SerializadorJSON();

    @Test
    void columnasDeCadaInforme() {
        assertEquals(List.of("Código", "Nombres", "Apellidos", "Programa", "Rol"),
                PanelInformes.columnas(TipoInforme.USUARIOS_REGISTRADOS));
        assertEquals(List.of("Código", "Nombre", "Cantidad de sesiones"), PanelInformes.columnas(TipoInforme.USUARIOS_CONECTADOS));
        assertEquals(List.of("Hora", "Tipo", "Canal", "Remitente", "Destino", "Hash"), PanelInformes.columnas(TipoInforme.MENSAJES));
        assertEquals(List.of("Hora", "Tipo", "Usuario", "IP", "Descripción"), PanelInformes.columnas(TipoInforme.LOGS));
    }

    @Test
    void filasDeCadaInformeDesdeElJson() {
        assertArrayEquals(new Object[]{"20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO},
                filas(TipoInforme.USUARIOS_REGISTRADOS, ANA).get(0));
        assertArrayEquals(new Object[]{"20201001", "Ana Pérez", 2},
                filas(TipoInforme.USUARIOS_CONECTADOS, new ConexionUsuarioDTO("20201001", "Ana Pérez", 2)).get(0));
        assertArrayEquals(new Object[]{"09:30:15", TipoMensaje.TEXTO, Canal.FORO, "20201001", "todos", "abcd…wxyz"},
                filas(TipoInforme.MENSAJES, texto()).get(0));
        assertArrayEquals(new Object[]{"09:30:15", TipoEvento.ERROR, "—", "—", "fallo"},
                filas(TipoInforme.LOGS, new EventoLogDTO("e1", TipoEvento.ERROR, "fallo", null, null, T)).get(0));
    }

    @Test
    void privadoMuestraSuDestino() {
        var privado = new MensajeDTO("m2", TipoMensaje.TEXTO, Canal.PRIVADO, "20201001", "20201002", T, HASH,
                "hola", 4, 1, null, List.of());
        assertEquals("20201002", filas(TipoInforme.MENSAJES, privado).get(0)[4]);
    }

    @Test
    void fichaDeUnMensajeDeImagen() {
        var imagen = new MensajeDTO("m3", TipoMensaje.IMAGEN, Canal.PRIVADO, "20201001", "ADMIN01", T, HASH,
                null, 0, 0, "foto.png", List.of(new ImagenVersionDTO(0, TipoFiltro.ORIGINAL, "foto.png",
                "almacen/originales/m3_foto.png", 12_595, "h")));

        assertEquals(List.of(
                "ID: m3",
                "Tipo: IMAGEN",
                "Canal: PRIVADO",
                "Remitente: 20201001 (Ana Pérez)",
                "Destino: ADMIN01",
                "Fecha: 2026-10-25 09:30:15.123",
                "SHA-256: " + HASH,
                "Archivo: foto.png (12,3 KB)"), texto(PanelInformes.lineasDetalle(imagen, Map.of("20201001", "Ana Pérez"))));
    }

    @Test
    void fichaDeUnMensajeDeTexto() {
        List<String> l = texto(PanelInformes.lineasDetalle(texto(), Map.of()));
        assertTrue(l.contains("Remitente: 20201001"), l.toString());
        assertTrue(l.contains("Destino: Todos"), l.toString());
        assertTrue(l.contains("Contenido: 4 caracteres, 1 palabras"), l.toString());
        assertTrue(l.stream().noneMatch(s -> s.contains("·") || s.contains("—")), l.toString());
    }

    @Test
    void atributoEnNegritaYValorEscapado() {
        assertEquals("<html><b>Archivo:</b> a&lt;b&gt;.png</html>",
                PanelInformes.htmlLinea(new String[]{"Archivo", "a<b>.png"}));
    }

    @Test
    void tamanoLegible() {
        assertEquals("512 B", PanelInformes.tamano(512));
        assertEquals("12,3 KB", PanelInformes.tamano(12_595));
        assertEquals("1,5 MB", PanelInformes.tamano(1_572_864));
    }

    @Test
    void mostrarLlenaLaTabla() {
        var panel = new PanelInformes(new FachadaUsuarios(), json);

        panel.mostrar(TipoInforme.USUARIOS_REGISTRADOS, informe(TipoInforme.USUARIOS_REGISTRADOS, ANA));
        assertEquals(1, panel.filasVisibles());
        assertEquals("Pérez", panel.celda(0, 2));

        panel.mostrar(TipoInforme.MENSAJES, informe(TipoInforme.MENSAJES, texto(), texto2()));
        assertEquals(2, panel.filasVisibles());
    }

    @Test
    void consultarPideElInformeALaFachada() {
        var fachada = new FachadaUsuarios();
        var panel = new PanelInformes(fachada, json);

        panel.consultar(TipoInforme.LOGS);

        assertEquals(List.of(TipoInforme.LOGS), fachada.pedidos);
        assertEquals(0, panel.filasVisibles());
    }

    private List<Object[]> filas(TipoInforme t, Object fila) {
        return PanelInformes.filas(t, json.desdeJson(informe(t, fila), InformeDTO.class), json);
    }

    private static List<String> texto(List<String[]> pares) {
        return pares.stream().map(p -> p[0] + ": " + p[1]).toList();
    }

    private String informe(TipoInforme t, Object... filas) {
        return json.aJson(new InformeDTO(t, T, "ADMIN01", List.of(filas)));
    }

    private static MensajeDTO texto() {
        return new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.FORO, "20201001", null, T, HASH, "hola", 4, 1, null, List.of());
    }

    private static MensajeDTO texto2() {
        return new MensajeDTO("m4", TipoMensaje.TEXTO, Canal.FORO, "20201001", null, T, HASH, "chao", 4, 1, null, List.of());
    }

    /** Fachada con un usuario y que devuelve informes vacíos. */
    private final class FachadaUsuarios implements IFachadaServidor {
        final java.util.List<TipoInforme> pedidos = new java.util.ArrayList<>();

        @Override
        public String informe(TipoInforme t, Map<String, String> filtros) {
            pedidos.add(t);
            return json.aJson(new InformeDTO(t, T, "ADMIN01", List.of()));
        }

        @Override public List<EstadoUsuarioDTO> listarUsuarios() { return List.of(new EstadoUsuarioDTO(ANA, true, 1)); }
        @Override public void iniciarServidor() { }
        @Override public void detenerServidor() { }
        @Override public void publicarEnForo(ContenidoDTO c) { }
        @Override public void enviarPrivado(String destino, ContenidoDTO c) { }
        @Override public List<SesionDTO> listarSesiones() { return List.of(); }
        @Override public void cerrarSesion(String idSesion) { }
        @Override public void suscribir(IObservadorServidor o) { }
        @Override public String getIpServidor() { return "127.0.0.1"; }
        @Override public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) { return Optional.empty(); }
    }
}
