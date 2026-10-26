package com.universidad.mensajeria.cliente.inicio;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.fachada.IObservadorCliente;
import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.cliente.utilerias.ConfiguracionCliente;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase 12: dos clientes completos (el mismo ensamblado de {@link AppCliente})
 * contra el servidor real. Necesita el servidor en marcha; se activa con
 * {@code -Pintegracion -Dcliente.e2e.servidor=host:puerto} y usa los usuarios
 * de {@code servidor/config/usuarios.txt} (Ana y Luis).
 *
 * <p>Es tolerante a los datos que ya tenga la BD del servidor: cada ejecución
 * marca sus mensajes con una etiqueta única.</p>
 */
@Tag("integracion")
class ClienteContraServidorRealTest {

    private static final String ANA = "20201001";
    private static final String LUIS = "20201002";
    private static final long ESPERA_S = 20;

    @TempDir
    Path dir;

    private final String etiqueta = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    private String host;
    private int puerto;
    private IFachadaCliente ana;
    private IFachadaCliente luis;
    private final Observador vistaAna = new Observador();
    private final Observador vistaLuis = new Observador();

    @BeforeEach
    void conectar() throws Exception {
        String servidor = System.getProperty("cliente.e2e.servidor", "");
        assumeTrue(!servidor.isBlank(), "sin -Dcliente.e2e.servidor=host:puerto");
        host = servidor.substring(0, servidor.lastIndexOf(':'));
        puerto = Integer.parseInt(servidor.substring(servidor.lastIndexOf(':') + 1));
        ana = AppCliente.ensamblar(configuracion());
        luis = AppCliente.ensamblar(configuracion());
        ana.suscribir(vistaAna);
        luis.suscribir(vistaLuis);
    }

    @AfterEach
    void salir() {
        if (ana != null) {
            ana.logout();
        }
        if (luis != null) {
            luis.logout();
        }
    }

    @Test
    void dosClientesRecorrenTodoElContrato() throws Exception {
        // 1. Login y catálogo de usuarios con su estado.
        String ip = ana.conectar(host, puerto);
        var malo = assertThrows(ExecutionException.class, () -> ana.login(ANA, "mala").get(ESPERA_S, TimeUnit.SECONDS));
        assertTrue(malo.getCause().getMessage() != null);
        ana.login(ANA, "clave123").get(ESPERA_S, TimeUnit.SECONDS);
        luis.conectar(host, puerto);
        luis.login(LUIS, "clave456").get(ESPERA_S, TimeUnit.SECONDS);

        List<EstadoUsuarioDTO> usuarios = ana.usuarios().get(ESPERA_S, TimeUnit.SECONDS);
        assertTrue(ip != null && !ip.isBlank());
        assertTrue(usuarios.size() >= 10, "todos los registrados son visibles");
        assertTrue(usuarios.stream().anyMatch(u -> u.usuario().rol() == Rol.ADMIN && u.conectado()));
        assertTrue(conectado(usuarios, ANA) && conectado(usuarios, LUIS));
        esperar(() -> vistaAna.usuarios.stream().anyMatch(l -> conectado(l, LUIS)),
                "Ana ve conectarse a Luis por USUARIO_CONECTADO");

        // 2. Foro concurrente: 5 mensajes de cada uno a la vez llegan a los dos.
        var envios = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 5; i++) {
            envios.add(ana.enviarForo(texto(etiqueta + " foro ana " + i)));
            envios.add(luis.enviarForo(texto(etiqueta + " foro luis " + i)));
        }
        CompletableFuture.allOf(envios.toArray(CompletableFuture[]::new)).get(ESPERA_S, TimeUnit.SECONDS);
        Predicate<MensajeDTO> delForo = m -> m.canal() == Canal.FORO && m.texto() != null && m.texto().startsWith(etiqueta + " foro");
        esperar(() -> vistaAna.contar(delForo) == 10 && vistaLuis.contar(delForo) == 10, "los 10 mensajes del foro en ambos");
        assertEquals(10, ana.historial("FORO").stream().filter(delForo).count(), "guardados en H2 de Ana");

        // 3. Privado: llega al destinatario y al remitente, y queda en el historial de los dos.
        ana.enviarPrivado(LUIS, texto(etiqueta + " privado")).get(ESPERA_S, TimeUnit.SECONDS);
        Predicate<MensajeDTO> privado = m -> m.canal() == Canal.PRIVADO && (etiqueta + " privado").equals(m.texto());
        esperar(() -> vistaAna.contar(privado) == 1 && vistaLuis.contar(privado) == 1, "el privado en ambos");
        esperar(() -> luis.historial(ANA).stream().anyMatch(privado), "el privado en el historial de Luis");

        // 4. Imagen: 6 versiones; el ORIGINAL de Ana sale de su H2 y Luis descarga un filtro.
        byte[] png = png();
        ana.enviarForo(new ContenidoDTO(TipoMensaje.IMAGEN, null, etiqueta + ".png",
                Base64.getEncoder().encodeToString(png))).get(ESPERA_S, TimeUnit.SECONDS);
        Predicate<MensajeDTO> laImagen = m -> m.tipo() == TipoMensaje.IMAGEN && (etiqueta + ".png").equals(m.nombreArchivo());
        esperar(() -> vistaLuis.contar(laImagen) == 1 && vistaAna.contar(laImagen) == 1, "la imagen en ambos");
        MensajeDTO imagen = vistaLuis.mensajes.stream().filter(laImagen).findFirst().orElseThrow();
        assertEquals(Arrays.asList(TipoFiltro.values()), imagen.imagenes().stream().map(v -> v.etapa()).toList());
        assertArrayEquals(png, ana.imagen(imagen.id(), TipoFiltro.ORIGINAL).get(ESPERA_S, TimeUnit.SECONDS));
        byte[] sepia = luis.imagen(imagen.id(), TipoFiltro.SEPIA).get(ESPERA_S, TimeUnit.SECONDS);
        assertTrue(sepia.length > 0);
        assertArrayEquals(sepia, luis.imagen(imagen.id(), TipoFiltro.SEPIA).get(ESPERA_S, TimeUnit.SECONDS));

        // 5. Validación de imágenes en el cliente: no llegan al servidor.
        var webp = assertThrows(ExecutionException.class, () -> ana.enviarForo(new ContenidoDTO(TipoMensaje.IMAGEN, null,
                "foto.webp", Base64.getEncoder().encodeToString(png))).get(ESPERA_S, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, webp.getCause());
        var grande = assertThrows(ExecutionException.class, () -> ana.enviarForo(new ContenidoDTO(TipoMensaje.IMAGEN, null,
                "grande.png", Base64.getEncoder().encodeToString(new byte[5_242_881]))).get(ESPERA_S, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, grande.getCause());

        // 6. Informes: los 4, con el JSON tal como llegó, y el filtro de LOGS.
        for (TipoInforme t : TipoInforme.values()) {
            InformeRecibido r = ana.informe(t, Map.of()).get(ESPERA_S, TimeUnit.SECONDS);
            assertEquals(t, r.informe().tipo());
            assertTrue(!r.informe().filas().isEmpty(), t + " sin filas");
            assertTrue(r.json().contains("\"filas\"") && r.json().contains("\n"), "JSON con sangría");
        }
        InformeRecibido logins = ana.informe(TipoInforme.LOGS, Map.of("tipoEvento", "LOGIN_OK")).get(ESPERA_S, TimeUnit.SECONDS);
        assertTrue(logins.informe().filas().stream().allMatch(f -> "LOGIN_OK".equals(((Map<?, ?>) f).get("tipo"))));
        InformeRecibido conectados = ana.informe(TipoInforme.USUARIOS_CONECTADOS, Map.of()).get(ESPERA_S, TimeUnit.SECONDS);
        assertTrue(conectados.informe().filas().stream().anyMatch(f -> LUIS.equals(((Map<?, ?>) f).get("codigo"))));

        // 7. Salida limpia: Luis sale sin «caída» y Ana lo ve desconectarse.
        long guardadosLuis = luis.mensajesGuardados();
        luis.logout();
        assertTrue(guardadosLuis >= 12, "Luis guardó en H2 lo de la sesión: " + guardadosLuis);
        esperar(() -> vistaAna.usuarios.stream().anyMatch(l -> !conectadoConSesion(l, LUIS)),
                "Ana ve desconectarse a Luis por USUARIO_DESCONECTADO");
        Thread.sleep(500);
        assertTrue(vistaLuis.desconexiones.isEmpty(), "el logout no se avisa como caída");
        assertTrue(vistaAna.desconexiones.isEmpty());
        assertTrue(luis.usuarioActual().isEmpty());
    }

    private ConfiguracionCliente configuracion() throws IOException {
        Path archivo = Files.createTempFile(dir, "cliente", ".properties");
        Files.writeString(archivo, """
                servidor.host=%s
                servidor.puerto=%d
                servidor.protocolo=TCP
                servidor.maxTramaBytes=16777216
                peticion.timeoutSegundos=15
                h2.url=jdbc:h2:%s/data/cliente_{codigo}
                h2.usuario=sa
                h2.clave=
                cache.mensajes.max=50
                """.formatted(host, puerto, dir.toAbsolutePath()), StandardCharsets.UTF_8);
        return ConfiguracionCliente.cargar(archivo);
    }

    private static ContenidoDTO texto(String t) {
        return new ContenidoDTO(TipoMensaje.TEXTO, t, null, null);
    }

    private static boolean conectado(List<EstadoUsuarioDTO> l, String codigo) {
        return l.stream().anyMatch(u -> u.usuario().codigo().equals(codigo) && u.conectado());
    }

    private static boolean conectadoConSesion(List<EstadoUsuarioDTO> l, String codigo) {
        return l.stream().anyMatch(u -> u.usuario().codigo().equals(codigo) && u.sesiones() > 0);
    }

    private static byte[] png() throws IOException {
        var img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 48; y++) {
                img.setRGB(x, y, new Color(x * 4, y * 5, 120).getRGB());
            }
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void esperar(BooleanSupplier condicion, String que) throws InterruptedException {
        for (int i = 0; i < ESPERA_S * 20 && !condicion.getAsBoolean(); i++) {
            Thread.sleep(50);
        }
        assertTrue(condicion.getAsBoolean(), "no ocurrió a tiempo: " + que);
    }

    /** Lo que vería una pantalla: mensajes, listas de usuarios y desconexiones. */
    private static final class Observador implements IObservadorCliente {
        final List<MensajeDTO> mensajes = new CopyOnWriteArrayList<>();
        final List<List<EstadoUsuarioDTO>> usuarios = new CopyOnWriteArrayList<>();
        final List<String> desconexiones = new CopyOnWriteArrayList<>();

        long contar(Predicate<MensajeDTO> p) {
            return mensajes.stream().filter(p).map(MensajeDTO::id).distinct().count();
        }

        @Override
        public void onMensaje(MensajeDTO m) {
            mensajes.add(m);
        }

        @Override
        public void onUsuarios(List<EstadoUsuarioDTO> l) {
            usuarios.add(l);
        }

        @Override
        public void onDesconexion(String motivo) {
            desconexiones.add(motivo);
        }
    }
}
