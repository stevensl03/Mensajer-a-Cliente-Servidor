package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.soporte.almacenar.cache.CacheL1Caffeine;
import com.universidad.mensajeria.utilerias.GeneradorHashSHA256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenarInformacionTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 14, 9, 30);

    @TempDir
    Path dir;

    private Path raiz;
    private RepositoriosEnMemoria repos;
    private AlmacenarInformacion almacen;

    @BeforeEach
    void preparar() throws IOException {
        raiz = dir.resolve("almacen");
        Path usuarios = Files.writeString(dir.resolve("usuarios.txt"),
                "codigo;nombres;apellidos;contrasena;programa;rol\n"
                        + "20201001;Ana;Pérez;clave123;Sistemas;USUARIO\n"
                        + "ADMIN01;Servidor;Administrador;admin2026;Administración;ADMIN\n", StandardCharsets.UTF_8);
        repos = new RepositoriosEnMemoria(raiz);
        almacen = crear(usuarios);
    }

    private AlmacenarInformacion crear(Path usuarios) {
        return new AlmacenarInformacion(repos, repos.archivos(),
                new ImportadorUsuarios(usuarios, new GeneradorHashSHA256()),
                new CacheL1Caffeine<>(100, Duration.ofMinutes(5)), new CacheL1Caffeine<>(100, Duration.ofMinutes(5)));
    }

    @Test
    void inicializarImportaUsuariosSoloSiLaTablaEstaVacia() {
        almacen.inicializar();
        assertEquals(2, almacen.usuariosRegistrados().size());

        almacen.inicializar();
        assertEquals(2, almacen.usuariosRegistrados().size());
    }

    @Test
    void conUsuariosEnLaBdNoLeeElArchivo() {
        repos.usuarios.add(new UsuarioRegistroDTO("X1", "X", "Y", "a".repeat(64), "P", Rol.ADMIN));
        var sinArchivo = crear(dir.resolve("no-existe.txt"));

        sinArchivo.inicializar();

        assertEquals(1, sinArchivo.usuariosRegistrados().size());
    }

    @Test
    void inicializarCierraLasConexionesHuerfanas() {
        almacen.registrarConexion(new SesionDTO("s1", "20201001", "Ana Pérez", "10.0.0.5", AHORA));
        almacen.registrarConexion(new SesionDTO("s2", "20201001", "Ana Pérez", "10.0.0.6", AHORA));
        almacen.registrarDesconexion("s1", AHORA.plusMinutes(1));

        almacen.inicializar();

        assertEquals(AHORA.plusMinutes(1), repos.conexiones.get("s1"));
        assertNotNull(repos.conexiones.get("s2"));
    }

    @Test
    void textoSeGuardaEnTextosYEnLaBd() throws IOException {
        MensajeDTO m = texto("m1");

        MensajeDTO guardado = almacen.guardarMensaje(m, List.of());

        assertEquals(m, guardado);
        assertEquals("Hola, ñandú", Files.readString(raiz.resolve("textos/m1.txt"), StandardCharsets.UTF_8));
        assertEquals(List.of(m), repos.mensajes);
    }

    @Test
    void imagenSeGuardaConLasRutasDelDiseno() {
        MensajeDTO guardado = almacen.guardarMensaje(imagen("m2"), seisVersiones());

        List<ImagenVersionDTO> versiones = guardado.imagenes();
        assertEquals(6, versiones.size());
        assertEquals(raiz.resolve("originales/m2_foto.png").toString(), versiones.get(0).rutaArchivo());
        assertEquals("foto.png", versiones.get(0).nombreArchivo());
        assertEquals(raiz.resolve("filtradas/m2_1_ESCALA_GRISES.png").toString(), versiones.get(1).rutaArchivo());
        assertEquals(raiz.resolve("filtradas/m2_5_REDUCCION_TAMANO.png").toString(), versiones.get(5).rutaArchivo());
        for (int i = 0; i < 6; i++) {
            assertEquals(i, versiones.get(i).orden());
            assertEquals(i + 1L, versiones.get(i).tamanoBytes());
            assertTrue(Files.exists(Path.of(versiones.get(i).rutaArchivo())));
        }
        assertEquals(guardado, repos.mensajes.get(0));
    }

    @Test
    void siFallaLaTransaccionSeBorranLosArchivos() throws IOException {
        repos.fallarAlGuardarMensajes = true;

        assertThrows(IllegalStateException.class, () -> almacen.guardarMensaje(imagen("m3"), seisVersiones()));
        assertThrows(IllegalStateException.class, () -> almacen.guardarMensaje(texto("m4"), List.of()));

        try (Stream<Path> archivos = Files.walk(raiz)) {
            assertEquals(0, archivos.filter(Files::isRegularFile).count());
        }
        assertTrue(repos.mensajes.isEmpty());
    }

    @Test
    void imagenInexistente() {
        assertTrue(almacen.leerImagen("no-existe", TipoFiltro.ORIGINAL).isEmpty());
    }

    @Test
    void leerImagenDesdeDiscoYLuegoDesdeCache() throws IOException {
        MensajeDTO guardado = almacen.guardarMensaje(imagen("m6"), seisVersiones());

        assertArrayEquals(new byte[]{1, 1, 1, 1, 1}, almacen.leerImagen("m6", TipoFiltro.BRILLO).orElseThrow());

        Files.delete(Path.of(guardado.imagenes().get(4).rutaArchivo()));
        assertArrayEquals(new byte[]{1, 1, 1, 1, 1}, almacen.leerImagen("m6", TipoFiltro.BRILLO).orElseThrow(),
                "la segunda lectura sale de la caché");
    }

    @Test
    void unMensajeDeOtraEjecucionSeBuscaEnElHistorial() {
        MensajeDTO guardado = almacen.guardarMensaje(imagen("m7"), seisVersiones());
        var reiniciado = crear(dir.resolve("usuarios.txt")); // cachés vacías, como tras un reinicio

        assertArrayEquals(new byte[]{1}, reiniciado.leerImagen("m7", TipoFiltro.ORIGINAL).orElseThrow());
        assertEquals(1, repos.lecturasDeMensajes);
        assertEquals(guardado, reiniciado.historialMensajes().get(0));
    }

    @Test
    void logsIdaYVuelta() {
        var e = new EventoLogDTO("e1", TipoEvento.LOGIN_OK, "LOGIN_OK", "20201001", "10.0.0.5", AHORA);
        almacen.guardarLog(e);
        assertEquals(List.of(e), almacen.historialLogs());
    }

    private static MensajeDTO texto(String id) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.FORO, "20201001", null, AHORA, "h".repeat(64),
                "Hola, ñandú", 11, 2, null, List.of());
    }

    private static MensajeDTO imagen(String id) {
        return new MensajeDTO(id, TipoMensaje.IMAGEN, Canal.PRIVADO, "20201001", "ADMIN01", AHORA, "h".repeat(64),
                null, 0, 0, "foto.png", List.of());
    }

    /** ORIGINAL con 1 byte, ESCALA_GRISES con 2 ... REDUCCION_TAMANO con 6. */
    private static List<ImagenProcesadaDTO> seisVersiones() {
        var lista = new ArrayList<ImagenProcesadaDTO>();
        for (TipoFiltro etapa : TipoFiltro.values()) {
            byte[] datos = new byte[etapa.ordinal() + 1];
            java.util.Arrays.fill(datos, (byte) 1);
            lista.add(new ImagenProcesadaDTO(etapa, datos, "h" + etapa.ordinal()));
        }
        return lista;
    }
}
