package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sin JavaFX: ejecutores directos (D-10). */
class ChatViewModelTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Ingeniería de Sistemas", Rol.USUARIO);
    private static final UsuarioDTO LUIS = new UsuarioDTO("20201002", "Luis", "Gómez", "Electrónica", Rol.USUARIO);
    private static final UsuarioDTO ZOE = new UsuarioDTO("20201009", "Zoe", "Arias", "Sistemas", Rol.USUARIO);
    private static final UsuarioDTO BEA = new UsuarioDTO("20201005", "Beatriz", "Ruiz", "Sistemas", Rol.USUARIO);
    private static final UsuarioDTO ADMIN = new UsuarioDTO("ADMIN01", "Admin", "Sistema", "TI", Rol.ADMIN);
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 25, 20, 5);

    @TempDir
    Path dir;

    private FachadaFalsa fachada;
    private AtomicInteger salidas;
    private AtomicInteger informes;
    private List<String> perdidas;
    private ChatViewModel vm;

    @BeforeEach
    void preparar() {
        fachada = new FachadaFalsa();
        fachada.actual = ANA;
        fachada.guardados = 12;
        fachada.listaUsuarios = List.of(new EstadoUsuarioDTO(ZOE, false, 0), new EstadoUsuarioDTO(LUIS, true, 1),
                new EstadoUsuarioDTO(ANA, true, 1), new EstadoUsuarioDTO(ADMIN, true, 0), new EstadoUsuarioDTO(BEA, true, 2));
        fachada.historiales.put("FORO", List.of(foro("f2", LUIS, 2), foro("f1", ZOE, 1)));
        salidas = new AtomicInteger();
        informes = new AtomicInteger();
        perdidas = new CopyOnWriteArrayList<>();
        vm = nuevo(Duration.ofSeconds(30));
    }

    private ChatViewModel nuevo(Duration espera) {
        return new ChatViewModel(fachada, Runnable::run, Runnable::run, espera,
                salidas::incrementAndGet, informes::incrementAndGet, perdidas::add);
    }

    @Test
    void iniciarCargaCabeceraUsuariosForoYBarraDeEstado() {
        vm.iniciar();

        assertEquals("Ana Pérez", vm.nombreUsuarioProperty().get());
        assertEquals("AP", vm.inicialesProperty().get());
        assertEquals("Ingeniería de Sistemas", vm.programaProperty().get());
        assertEquals("Estoy conectado a: 192.168.20.31 · TCP :5000", vm.estadoConexionProperty().get());
        assertTrue(vm.conectadoProperty().get());
        assertEquals("Sesión local H2 #3 · 12 mensajes guardados", vm.resumenSesionProperty().get());
        assertEquals(List.of("f1", "f2"), ids(vm.foro()), "el foro queda en orden cronológico");
        assertEquals(4, vm.conectadosProperty().get());
    }

    @Test
    void ordenaAdminConectadosYDemasAlfabeticamenteYFiltra() {
        vm.iniciar();

        assertEquals(List.of("ADMIN01", "20201001", "20201005", "20201002", "20201009"), codigos(vm.usuariosVisibles()));

        vm.filtroProperty().set("gómez");
        assertEquals(List.of("20201002"), codigos(vm.usuariosVisibles()));
        vm.filtroProperty().set("2020100");
        assertEquals(4, vm.usuariosVisibles().size());
        vm.filtroProperty().set("");
        assertEquals(5, vm.usuariosVisibles().size());
    }

    @Test
    void losMensajesNuevosSeReparteSinDuplicarYEnOrden() {
        vm.iniciar();

        vm.onMensaje(foro("f0", LUIS, 0));
        vm.onMensaje(foro("f2", LUIS, 2));
        vm.onMensaje(privado("p1", LUIS, ANA, 5));
        vm.onMensaje(privado("p2", ANA, LUIS, 6));

        assertEquals(List.of("f0", "f1", "f2"), ids(vm.foro()));
        assertEquals(List.of("p1", "p2"), ids(vm.privados().get("20201002")), "la clave es el otro usuario");
        assertEquals(1, vm.privados().size());
    }

    @Test
    void abrirPrivadoCargaSuHistorialYNoPermiteHablarConsigoMismo() {
        fachada.historiales.put("20201002", List.of(privado("p0", LUIS, ANA, 0)));
        vm.iniciar();

        assertFalse(vm.abrirPrivado("20201001"), "no hay conversación con uno mismo (R-34)");
        assertTrue(vm.abrirPrivado("20201002"));
        assertTrue(vm.abrirPrivado("20201002"));

        assertEquals(List.of("p0"), ids(vm.privados().get("20201002")));
        assertEquals(1, fachada.llamadas.stream().filter("historial 20201002"::equals).count());
        assertFalse(vm.privados().containsKey("20201001"));
    }

    @Test
    void enviarMarcaEnviandoHastaQueLlegaElMensajePropio() {
        vm.iniciar();

        vm.enviarForo("   ");
        assertTrue(fachada.contenidos.isEmpty(), "un texto en blanco no se envía");
        vm.enviarForo("Hola foro");
        vm.enviarPrivado("20201002", "Hola Luis");

        assertTrue(vm.enviandoProperty().get());
        assertEquals("Hola foro", fachada.contenidos.get(0).texto());
        assertTrue(fachada.llamadas.contains("enviarPrivado 20201002"));
        assertTrue(vm.foro().stream().noneMatch(m -> "Hola foro".equals(m.texto())), "no se pinta antes de MENSAJE_NUEVO (R-33)");
        vm.onMensaje(foro("m1", ANA, 9));
        assertTrue(vm.enviandoProperty().get(), "aún falta uno");
        vm.onMensaje(privado("m2", ANA, LUIS, 10));
        assertFalse(vm.enviandoProperty().get());
    }

    @Test
    void unRechazoMuestraElMensajeDelServidor() {
        fachada.respuestaEnvio = () -> CompletableFuture.failedFuture(
                new ExcepcionServidor(CodigoError.DESTINO_INEXISTENTE, "El destinatario no existe: 9"));
        vm.iniciar();

        vm.enviarPrivado("9", "hola");

        assertFalse(vm.enviandoProperty().get());
        assertEquals("El destinatario no existe: 9", vm.errorProperty().get());
    }

    @Test
    void siNoLlegaElMensajePropioAvisaQueNoSeConfirmo() throws Exception {
        vm = nuevo(Duration.ofMillis(50));
        vm.iniciar();

        vm.enviarForo("¿llegará?");
        Thread.sleep(300);

        assertFalse(vm.enviandoProperty().get());
        assertEquals(ChatViewModel.SIN_CONFIRMAR, vm.errorProperty().get());
    }

    @Test
    void validaLaImagenAntesDeLeerlaYLaEnviaEnBase64() throws Exception {
        vm.iniciar();
        Path texto = Files.writeString(dir.resolve("notas.txt"), "x");
        Path grande = Files.write(dir.resolve("grande.png"), new byte[5_242_881]);
        Path foto = Files.write(dir.resolve("foto.PNG"), new byte[] {1, 2, 3});

        vm.enviarImagen(null, texto.toFile());
        assertEquals("Solo se aceptan imágenes png, jpg, jpeg, gif o bmp.", vm.errorProperty().get());
        vm.enviarImagen(null, grande.toFile());
        assertEquals("La imagen debe pesar como máximo 5 MB.", vm.errorProperty().get());
        assertTrue(fachada.contenidos.isEmpty());

        vm.enviarImagen("20201002", foto.toFile());

        var c = fachada.contenidos.get(0);
        assertEquals(TipoMensaje.IMAGEN, c.tipo());
        assertEquals("foto.PNG", c.nombreArchivo());
        assertEquals(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}), c.datosBase64());
        assertTrue(fachada.llamadas.contains("enviarPrivado 20201002"));
        assertEquals("", vm.errorProperty().get());
    }

    @Test
    void laDesconexionAvisaUnaVezEnPalabrasYDejaDeAtender() {
        vm.iniciar();

        vm.onDesconexion("CERRADA_POR_ADMIN");
        vm.onDesconexion("El servidor cerró la conexión");
        vm.onMensaje(foro("tarde", LUIS, 30));

        assertEquals(List.of("El administrador cerró tu sesión."), perdidas);
        assertFalse(vm.conectadoProperty().get());
        assertEquals("Desconectado: El administrador cerró tu sesión.", vm.estadoConexionProperty().get());
        assertTrue(vm.foro().stream().noneMatch(m -> m.id().equals("tarde")));
    }

    @Test
    void salirHaceLogoutYVuelveAlLoginSinAvisarCaida() {
        vm.iniciar();

        vm.salir();
        vm.onDesconexion("Conexión cerrada por el cliente");

        assertTrue(fachada.llamadas.contains("logout"));
        assertEquals(1, salidas.get());
        assertTrue(perdidas.isEmpty());
    }

    @Test
    void unNuevoInicioEmpiezaDeCero() {
        vm.iniciar();
        vm.abrirPrivado("20201002");
        vm.onMensaje(foro("f9", LUIS, 9));
        vm.salir();

        fachada.actual = LUIS;
        fachada.historiales.clear();
        vm.iniciar();

        assertTrue(vm.foro().isEmpty());
        assertTrue(vm.privados().isEmpty());
        assertEquals("Luis Gómez", vm.nombreUsuarioProperty().get());
    }

    @Test
    void encabezadosYAyudaSegunLaConversacion() {
        vm.iniciar();

        assertEquals("Tú · 20:05", vm.encabezado(foro("a", ANA, 0)));
        assertEquals("Luis Gómez · 20:06", vm.encabezado(foro("b", LUIS, 60)));
        assertEquals("Escribe un mensaje para el foro…", vm.ayudaEscritura(ChatViewModel.FORO));
        assertEquals("Escribe un mensaje para Luis Gómez…", vm.ayudaEscritura("20201002"));
        assertEquals("99999999", vm.nombre("99999999"));
        vm.informes();
        assertEquals(1, informes.get());
    }

    private static MensajeDTO foro(String id, UsuarioDTO de, int segundos) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.FORO, de.codigo(), null, T0.plusSeconds(segundos), "h",
                "texto " + id, 7, 2, null, List.of());
    }

    private static MensajeDTO privado(String id, UsuarioDTO de, UsuarioDTO para, int segundos) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.PRIVADO, de.codigo(), para.codigo(), T0.plusSeconds(segundos),
                "h", "texto " + id, 7, 2, null, List.of());
    }

    private static List<String> ids(List<MensajeDTO> l) {
        return l.stream().map(MensajeDTO::id).toList();
    }

    private static List<String> codigos(List<EstadoUsuarioDTO> l) {
        return l.stream().map(e -> e.usuario().codigo()).toList();
    }
}
