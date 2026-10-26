package com.universidad.mensajeria.presentacion.escritorio;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lógica de S2 · Chat ADMIN. Los componentes Swing se pueden crear sin pantalla. */
class PanelChatAdminTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 25, 9, 30);
    private static final UsuarioDTO ADMIN = new UsuarioDTO("ADMIN01", "Servidor", "Administrador", "Adm", Rol.ADMIN);
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final UsuarioDTO LUIS = new UsuarioDTO("20201002", "Luis", "Gómez", "Civil", Rol.USUARIO);
    private static final UsuarioDTO MARIA = new UsuarioDTO("20201003", "María", "Rodríguez", "Electrónica", Rol.USUARIO);
    private static final List<EstadoUsuarioDTO> ESTADOS = List.of(
            new EstadoUsuarioDTO(ADMIN, true, 0), new EstadoUsuarioDTO(ANA, true, 2),
            new EstadoUsuarioDTO(LUIS, true, 1), new EstadoUsuarioDTO(MARIA, false, 0));

    private final SerializadorJSON json = new SerializadorJSON();
    private final FachadaGrabadora fachada = new FachadaGrabadora();

    @Test
    void claveDeConversacionDesdeElPuntoDeVistaDelAdmin() {
        assertEquals(Optional.of("FORO"), PanelChatAdmin.clave(foro("m1", ANA), ADMIN.codigo()));
        assertEquals(Optional.of(ANA.codigo()), PanelChatAdmin.clave(privado("m2", ANA, ADMIN), ADMIN.codigo()));
        assertEquals(Optional.of(ANA.codigo()), PanelChatAdmin.clave(privado("m3", ADMIN, ANA), ADMIN.codigo()));
        assertTrue(PanelChatAdmin.clave(privado("m4", ANA, LUIS), ADMIN.codigo()).isEmpty(), "privado entre otros");
    }

    @Test
    void leeElHistorialDesdeElJsonDelInforme() {
        var mensajes = List.of(foro("m1", ANA), privado("m2", ANA, ADMIN));
        String informe = json.aJson(new InformeDTO(TipoInforme.MENSAJES, T0, ADMIN.codigo(), mensajes));

        assertEquals(mensajes, PanelChatAdmin.leerInforme(informe, json));
    }

    @Test
    void filtraConectadosSinElAdminYPorBusqueda() {
        assertEquals(List.of(ANA.codigo(), LUIS.codigo()), codigos(PanelChatAdmin.filtrarConectados(ESTADOS, "ADMIN01", "")));
        assertEquals(List.of(LUIS.codigo()), codigos(PanelChatAdmin.filtrarConectados(ESTADOS, "ADMIN01", " gómez ")));
        assertEquals(List.of(ANA.codigo()), codigos(PanelChatAdmin.filtrarConectados(ESTADOS, "ADMIN01", "20201001")));
    }

    @Test
    void validaElAdjunto() throws IOException {
        byte[] png = UtilImagen.aBytes(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png");

        assertTrue(PanelChatAdmin.validarAdjunto("foto.PNG", png, 1024).isEmpty());
        assertTrue(PanelChatAdmin.validarAdjunto("foto.exe", png, 1024).isPresent(), "extensión");
        assertTrue(PanelChatAdmin.validarAdjunto("foto.png", png, png.length - 1).isPresent(), "tamaño");
        assertTrue(PanelChatAdmin.validarAdjunto("foto.png", new byte[]{1, 2, 3}, 1024).isPresent(), "ilegible");
        assertTrue(PanelChatAdmin.validarAdjunto("foto.png", new byte[0], 1024).isPresent(), "vacía");
    }

    @Test
    void validaElTextoContandoCaracteres() {
        assertTrue(PanelChatAdmin.validarTexto("hola", 4).isEmpty());
        assertTrue(PanelChatAdmin.validarTexto("😀😀😀😀", 4).isEmpty(), "un emoji es un carácter");
        assertTrue(PanelChatAdmin.validarTexto("holas", 4).isPresent());
        assertTrue(PanelChatAdmin.validarTexto("   ", 4).isPresent());
    }

    @Test
    void historialRepartidoEnConversacionesYSinDuplicados() {
        var panel = nuevoPanel();
        panel.cargarHistorial(json.aJson(new InformeDTO(TipoInforme.MENSAJES, T0, ADMIN.codigo(), List.of(
                foro("m1", ANA), foro("m2", ADMIN), privado("m3", ANA, ADMIN), privado("m4", ANA, LUIS)))));

        assertEquals(List.of("FORO"), panel.pestanasAbiertas());
        assertEquals(2, panel.burbujas("FORO"));

        panel.abrirConversacion(ANA.codigo());
        assertEquals(List.of("FORO", ANA.codigo()), panel.pestanasAbiertas());
        assertEquals(1, panel.burbujas(ANA.codigo()), "el privado Ana-Luis no va al chat del ADMIN");

        panel.mostrarMensaje(foro("m1", ANA));
        assertEquals(2, panel.burbujas("FORO"), "un mensaje repetido no se dibuja dos veces");
    }

    @Test
    void unPrivadoNuevoAbreLaPestanaDeEseUsuario() {
        var panel = nuevoPanel();

        panel.mostrarMensaje(privado("m5", LUIS, ADMIN));
        panel.mostrarMensaje(privado("m6", ADMIN, LUIS));
        panel.mostrarMensaje(privado("m7", ANA, LUIS));

        assertEquals(List.of("FORO", LUIS.codigo()), panel.pestanasAbiertas());
        assertEquals(2, panel.burbujas(LUIS.codigo()));
    }

    @Test
    void soloLosTextosLargosLlevanAnchoFijo() {
        assertEquals("<html>Hola Ana</html>", PanelChatAdmin.htmlTexto("Hola Ana"));
        assertTrue(PanelChatAdmin.htmlTexto("x".repeat(PanelChatAdmin.CARACTERES_POR_LINEA + 1)).contains("width:360px"));
        assertEquals("<html>a &lt;b&gt;<br>c</html>", PanelChatAdmin.htmlTexto("a <b>\nc"));
    }

    @Test
    void laListaDeConectadosSeRefresca() {
        var panel = nuevoPanel();
        assertEquals(List.of(ANA.codigo(), LUIS.codigo()), panel.listaConectados());
    }

    @Test
    void enviarUsaLaFachada() {
        var panel = nuevoPanel();
        var texto = new ContenidoDTO(TipoMensaje.TEXTO, "Hola a todos", null, null);

        panel.publicarEnForo(texto);
        panel.enviarPrivado(ANA.codigo(), texto);

        assertEquals(List.of("FORO:Hola a todos", "20201001:Hola a todos"), fachada.enviados);
    }

    private PanelChatAdmin nuevoPanel() {
        var panel = new PanelChatAdmin(fachada, json, 4000, 5_242_880);
        panel.refrescarConectados(ESTADOS);
        return panel;
    }

    private static MensajeDTO foro(String id, UsuarioDTO de) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.FORO, de.codigo(), null, T0, "h", "texto " + id, 7, 2,
                null, List.of());
    }

    private static MensajeDTO privado(String id, UsuarioDTO de, UsuarioDTO para) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.PRIVADO, de.codigo(), para.codigo(), T0, "h",
                "texto " + id, 7, 2, null, List.of());
    }

    private static List<String> codigos(List<EstadoUsuarioDTO> l) {
        return l.stream().map(e -> e.usuario().codigo()).toList();
    }

    /** Fachada que graba los envíos del ADMIN. */
    private static final class FachadaGrabadora implements IFachadaServidor {
        final List<String> enviados = new ArrayList<>();

        @Override public void publicarEnForo(ContenidoDTO c) { enviados.add("FORO:" + c.texto()); }
        @Override public void enviarPrivado(String destino, ContenidoDTO c) { enviados.add(destino + ":" + c.texto()); }
        @Override public List<EstadoUsuarioDTO> listarUsuarios() { return ESTADOS; }
        @Override public void iniciarServidor() { }
        @Override public void detenerServidor() { }
        @Override public String informe(TipoInforme t, Map<String, String> filtros) { return "{}"; }
        @Override public List<SesionDTO> listarSesiones() { return List.of(); }
        @Override public void cerrarSesion(String idSesion) { }
        @Override public void suscribir(IObservadorServidor o) { }
        @Override public String getIpServidor() { return "127.0.0.1"; }
        @Override public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) { return Optional.empty(); }
    }
}
