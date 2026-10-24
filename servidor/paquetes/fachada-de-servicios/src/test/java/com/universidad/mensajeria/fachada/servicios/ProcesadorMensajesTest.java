package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.servicios.EnrutadorMensajesTest.VistaGrabadora;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ANA;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.LUIS;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.MARIA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcesadorMensajesTest {

    private final GestorConexiones conexiones = new GestorConexiones(10);
    private final ServiciosFalso servicios = new ServiciosFalso();
    private final AlmacenMensajesFalso almacen = new AlmacenMensajesFalso();
    private final LogGrabador log = new LogGrabador();
    private final VistaGrabadora vista = new VistaGrabadora();
    private final ProcesadorMensajes procesador;

    private final CanalGrabador ana = abrir(ANA);
    private final CanalGrabador luis = abrir(LUIS);
    private final CanalGrabador maria = abrir(MARIA);

    ProcesadorMensajesTest() {
        var notificador = new NotificadorObservadores();
        notificador.suscribir(vista);
        var enrutador = new EnrutadorMensajes(conexiones, new ClienteServidorFalso(), notificador,
                ClienteServidorFalso.JSON);
        procesador = new ProcesadorMensajes(servicios, new FabricaMensajes(), almacen, log, enrutador);
    }

    @Test
    void textoAlForoSeGuardaRegistraYEntregaATodos() {
        procesador.procesar(solicitud("m1", Canal.FORO, null, new ContenidoDTO(TipoMensaje.TEXTO, "Hola a todos", null, null)));

        MensajeDTO m = almacen.mensajes.get(0);
        assertEquals("m1", m.id());
        assertEquals(ANA.codigo(), m.remitente());
        EventoLogDTO e = log.eventos.get(0);
        assertEquals(TipoEvento.MENSAJE_FORO, e.tipo());
        assertEquals("MENSAJE_FORO id=m1 de=20201001 tipo=TEXTO hash=h-12 caracteres=12 palabras=3", e.descripcion());
        assertEquals(ANA.codigo(), e.usuario());
        assertEquals(1, ana.enviados().size());
        assertEquals(1, luis.enviados().size());
        assertEquals(1, maria.enviados().size());
        assertEquals(List.of(m), vista.mensajes);
    }

    @Test
    void imagenPrivadaSeDecodificaYSeRegistraConElFormatoDeImagen() {
        byte[] bytes = {1, 2, 3, 4};
        var contenido = new ContenidoDTO(TipoMensaje.IMAGEN, null, "foto.png", Base64.getEncoder().encodeToString(bytes));

        procesador.procesar(solicitud("m2", Canal.PRIVADO, LUIS.codigo(), contenido));

        assertArrayEquals(bytes, servicios.ultimaImagen);
        assertEquals("foto.png", servicios.ultimoNombre);
        assertEquals(6, almacen.mensajes.get(0).imagenes().size());
        assertEquals("MENSAJE_PRIVADO id=m2 de=20201001 para=20201002 tipo=IMAGEN archivo=foto.png hash=h-4 filtros=5",
                log.eventos.get(0).descripcion());
        assertEquals(1, ana.enviados().size());
        assertEquals(1, luis.enviados().size());
        assertTrue(maria.enviados().isEmpty());
    }

    @Test
    void siElProcesamientoFallaSeRegistraErrorYNoHayPush() {
        servicios.fallar = true;

        procesador.procesar(solicitud("m3", Canal.FORO, null, new ContenidoDTO(TipoMensaje.IMAGEN, null, "x.png", "AAAA")));

        assertTrue(almacen.mensajes.isEmpty());
        assertEquals(List.of(TipoEvento.ERROR), log.tipos());
        assertTrue(log.eventos.get(0).descripcion().startsWith("ERROR id=m3 de=20201001"));
        assertTrue(ana.enviados().isEmpty());
        assertTrue(vista.mensajes.isEmpty());
    }

    @Test
    void siElGuardadoFallaSeRegistraErrorYNoHayPush() {
        almacen.fallar = true;

        procesador.procesar(solicitud("m4", Canal.FORO, null, new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null)));

        assertEquals(List.of(TipoEvento.ERROR), log.tipos());
        assertTrue(luis.enviados().isEmpty());
    }

    private CanalGrabador abrir(UsuarioDTO u) {
        var c = new CanalGrabador();
        conexiones.abrirSesion(c, u);
        return c;
    }

    private static SolicitudMensajeDTO solicitud(String id, Canal canal, String destino, ContenidoDTO contenido) {
        return new SolicitudMensajeDTO(id, "p1", "s1", ANA.codigo(), canal, destino, contenido, LocalDateTime.now());
    }
}
