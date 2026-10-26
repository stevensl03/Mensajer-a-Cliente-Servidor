package com.universidad.mensajeria.cliente.persistencia;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoriosH2Test {

    private static final String HASH = "a".repeat(64);
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 25, 20, 0, 27, 720_395_692);

    private ProveedorConexionH2 proveedor;
    private IRepositorioSesion sesiones;
    private IRepositorioMensajesLocal mensajes;
    private long sesion;

    @BeforeEach
    void preparar() {
        proveedor = new ProveedorConexionH2("jdbc:h2:mem:" + UUID.randomUUID() + "_{codigo}", "sa", "");
        proveedor.abrir(ANA.codigo());
        var fabrica = new FabricaRepositoriosH2(proveedor);
        sesiones = fabrica.sesiones();
        mensajes = fabrica.mensajes();
        sesion = sesiones.abrir(ANA, "192.168.20.31", 5000);
    }

    @AfterEach
    void cerrar() {
        proveedor.cerrar();
    }

    @Test
    void laFabricaEntregaSiempreLasMismasInstancias() {
        var fabrica = new FabricaRepositoriosH2(proveedor);
        assertSame(fabrica.sesiones(), fabrica.sesiones());
        assertSame(fabrica.mensajes(), fabrica.mensajes());
    }

    @Test
    void lasSesionesTienenIdsCrecientesYSeCierranUnaVez() throws SQLException {
        long otra = sesiones.abrir(ANA, "192.168.20.31", 5000);
        assertTrue(otra > sesion);
        assertNull(fin(sesion));

        sesiones.cerrar(sesion);
        LocalDateTime primerFin = fin(sesion);
        sesiones.cerrar(sesion);

        assertNotNull(primerFin);
        assertEquals(primerFin, fin(sesion), "cerrar de nuevo no cambia la hora de fin");
        assertNull(fin(otra));
    }

    @Test
    void guardaYLeeUnMensajeDelForo() {
        var m = texto("m1", Canal.FORO, "20201002", null, T0, "Hola foro");

        mensajes.guardar(m, sesion, false);

        List<MensajeDTO> foro = mensajes.conversacion(IRepositorioMensajesLocal.FORO);
        assertEquals(1, foro.size());
        MensajeDTO leido = foro.get(0);
        assertEquals("m1", leido.id());
        assertEquals("Hola foro", leido.texto());
        assertEquals(Canal.FORO, leido.canal());
        assertNull(leido.destinatario());
        assertEquals(HASH, leido.hash());
        assertEquals(9, leido.numCaracteres());
        assertEquals(2, leido.numPalabras());
        assertTrue(Duration.between(leido.fecha(), T0).abs().toMillis() < 1, "TIMESTAMP(3) pierde los nanos (R-58)");
    }

    @Test
    void unMensajeRepetidoQuedaUnaVezYConservaSuPrimeraSesion() throws SQLException {
        var m = texto("m1", Canal.FORO, ANA.codigo(), null, T0, "mío");
        long posterior = sesiones.abrir(ANA, "192.168.20.31", 5000);

        mensajes.guardar(m, sesion, true);
        mensajes.guardar(m, posterior, false);

        assertEquals(1, mensajes.contar());
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement("SELECT SESION_ID, PROPIO FROM MENSAJE WHERE ID = 'm1'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertEquals(sesion, rs.getLong(1));
            assertTrue(rs.getBoolean(2));
        }
    }

    @Test
    void elMismoMensajeGuardadoALaVezDesdeVariosHilosQuedaUnaVez() throws Exception {
        var m = texto("m1", Canal.FORO, "20201002", null, T0, "carrera");
        var salida = new CountDownLatch(1);
        List<Thread> hilos = new ArrayList<>();
        List<Throwable> errores = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int i = 0; i < 8; i++) {
            hilos.add(Thread.ofPlatform().start(() -> {
                try {
                    salida.await();
                    mensajes.guardar(m, sesion, false);
                } catch (Throwable t) {
                    errores.add(t);
                }
            }));
        }
        salida.countDown();
        for (Thread h : hilos) {
            h.join();
        }

        assertTrue(errores.isEmpty(), errores.toString());
        assertEquals(1, mensajes.contar());
    }

    @Test
    void unPrivadoTieneLaMismaClaveEnLosDosSentidosYSeOrdenaPorFecha() {
        mensajes.guardar(texto("b", Canal.PRIVADO, "20201002", "20201001", T0.plusSeconds(2), "segundo"), sesion, false);
        mensajes.guardar(texto("a", Canal.PRIVADO, "20201001", "20201002", T0, "primero"), sesion, true);
        mensajes.guardar(texto("c", Canal.PRIVADO, "20201001", "20201003", T0, "otra conversación"), sesion, true);
        mensajes.guardar(texto("d", Canal.FORO, "20201002", null, T0, "foro"), sesion, false);

        List<MensajeDTO> conv = mensajes.conversacion(IRepositorioMensajesLocal.claveConversacion("20201002", "20201001"));

        assertEquals(List.of("a", "b"), conv.stream().map(MensajeDTO::id).toList());
        assertEquals("20201001|20201002", IRepositorioMensajesLocal.claveConversacion(conv.get(1)));
        assertEquals(4, mensajes.contar());
    }

    @Test
    void enEmpateDeFechaOrdenaPorId() {
        mensajes.guardar(texto("z", Canal.FORO, "20201002", null, T0, "z"), sesion, false);
        mensajes.guardar(texto("k", Canal.FORO, "20201002", null, T0, "k"), sesion, false);

        assertEquals(List.of("k", "z"),
                mensajes.conversacion(IRepositorioMensajesLocal.FORO).stream().map(MensajeDTO::id).toList());
    }

    @Test
    void unaImagenSeLeeSinVersionesYSusAdjuntosSeGuardanPorEtapa() {
        var versiones = List.of(new ImagenVersionDTO(0, TipoFiltro.ORIGINAL, "foto.png", "a/foto.png", 3, HASH));
        var img = new MensajeDTO("img", TipoMensaje.IMAGEN, Canal.FORO, ANA.codigo(), null, T0, HASH,
                null, 0, 0, "foto.png", versiones);
        mensajes.guardar(img, sesion, true);

        mensajes.guardarAdjunto("img", TipoFiltro.ORIGINAL, "foto.png", new byte[] {1, 2, 3});
        mensajes.guardarAdjunto("img", TipoFiltro.SEPIA, "foto_sepia.png", new byte[] {4});
        mensajes.guardarAdjunto("img", TipoFiltro.SEPIA, "foto_sepia.png", new byte[] {5, 6});

        MensajeDTO leido = mensajes.conversacion(IRepositorioMensajesLocal.FORO).get(0);
        assertEquals(TipoMensaje.IMAGEN, leido.tipo());
        assertEquals("foto.png", leido.nombreArchivo());
        assertNull(leido.texto());
        assertTrue(leido.imagenes().isEmpty(), "D-07: el esquema no guarda las versiones");
        assertArrayEquals(new byte[] {1, 2, 3}, mensajes.adjunto("img", TipoFiltro.ORIGINAL).orElseThrow());
        assertArrayEquals(new byte[] {5, 6}, mensajes.adjunto("img", TipoFiltro.SEPIA).orElseThrow());
        assertFalse(mensajes.adjunto("img", TipoFiltro.BRILLO).isPresent());
    }

    @Test
    void unAdjuntoSinSuMensajeSeRechaza() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> mensajes.guardarAdjunto("no-existe", TipoFiltro.ORIGINAL, "x.png", new byte[] {1}));

        assertTrue(e.getMessage().contains("no-existe"), e.getMessage());
    }

    @Test
    void conLaBaseCerradaLosRepositoriosFallanConClaridad() {
        proveedor.cerrar();

        assertThrows(IllegalStateException.class, () -> mensajes.contar());
        assertThrows(IllegalStateException.class, () -> sesiones.abrir(ANA, "ip", 1));
    }

    private LocalDateTime fin(long id) throws SQLException {
        try (Connection c = proveedor.obtenerConexion();
             PreparedStatement ps = c.prepareStatement("SELECT FIN FROM SESION WHERE ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, LocalDateTime.class);
            }
        }
    }

    private static MensajeDTO texto(String id, Canal canal, String remitente, String destinatario,
                                    LocalDateTime fecha, String texto) {
        int palabras = texto.isBlank() ? 0 : texto.strip().split("\\s+").length;
        return new MensajeDTO(id, TipoMensaje.TEXTO, canal, remitente, destinatario, fecha, HASH, texto,
                texto.codePointCount(0, texto.length()), palabras, null, List.of());
    }
}
