package com.universidad.mensajeria.fachada.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrutadorMensajesTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final UsuarioDTO LUIS = new UsuarioDTO("20201002", "Luis", "Gómez", "Civil", Rol.USUARIO);
    private static final UsuarioDTO MARIA = new UsuarioDTO("20201003", "María", "Rodríguez", "Electrónica", Rol.USUARIO);
    private static final String ADMIN = "00000000";

    private final GestorConexiones conexiones = new GestorConexiones(10);
    private final NotificadorObservadores notificador = new NotificadorObservadores();
    private final VistaGrabadora vista = new VistaGrabadora();
    private final EnrutadorMensajes enrutador =
            new EnrutadorMensajes(conexiones, new ClienteServidorFalso(), notificador, ClienteServidorFalso.JSON);

    private final CanalGrabador ana1 = abrir(ANA);
    private final CanalGrabador ana2 = abrir(ANA);
    private final CanalGrabador luis = abrir(LUIS);
    private final CanalGrabador maria = abrir(MARIA);

    EnrutadorMensajesTest() {
        notificador.suscribir(vista);
    }

    @Test
    void foroLlegaATodasLasSesionesYALasVistas() {
        MensajeDTO m = mensaje("m1", Canal.FORO, ANA.codigo(), null);

        enrutador.entregar(m);

        for (CanalGrabador c : List.of(ana1, ana2, luis, maria)) {
            assertEquals(1, c.enviados().size());
        }
        assertEquals(List.of(m), vista.mensajes);
    }

    @Test
    void privadoLlegaAlDestinatarioYAlRemitenteNoATerceros() {
        enrutador.entregar(mensaje("p1", Canal.PRIVADO, ANA.codigo(), LUIS.codigo()));

        assertEquals(1, luis.enviados().size());
        assertEquals(1, ana1.enviados().size(), "el remitente lo ve en todas sus sesiones");
        assertEquals(1, ana2.enviados().size());
        assertTrue(maria.enviados().isEmpty());
        assertEquals(1, vista.mensajes.size());
    }

    @Test
    void privadoAlAdminLlegaALasVistasYAlRemitente() {
        MensajeDTO m = mensaje("p1", Canal.PRIVADO, LUIS.codigo(), ADMIN);

        enrutador.entregar(m);

        assertEquals(1, luis.enviados().size());
        assertTrue(ana1.enviados().isEmpty());
        assertTrue(maria.enviados().isEmpty());
        assertEquals(List.of(m), vista.mensajes, "es la bandeja del ADMIN");
    }

    @Test
    void elPushEsMensajeNuevoConElMensajeEnDatos() {
        enrutador.entregar(mensaje("m1", Canal.FORO, ANA.codigo(), null));

        JsonNode push = ClienteServidorFalso.JSON.desdeJson(luis.enviados().get(0), JsonNode.class);
        assertEquals("MENSAJE_NUEVO", push.get("evento").asText());
        assertEquals("m1", push.get("datos").get("mensaje").get("id").asText());
        assertTrue(push.has("fecha"));
        assertTrue(!push.has("idPeticion"), "un push nunca lleva idPeticion");
    }

    @Test
    void unCanalCaidoNoImpideEntregarALosDemas() {
        ana1.fallarAlEnviar = true;

        enrutador.difundir(enrutador.evento(TipoEventoPush.SESION_CERRADA, Map.of("motivo", "SERVIDOR_DETENIDO")));

        assertEquals(1, ana2.enviados().size());
        assertEquals(1, luis.enviados().size());
        assertEquals(1, maria.enviados().size());
    }

    @Test
    void enviarASoloLlegaALasSesionesDeEseUsuario() {
        enrutador.enviarA(ANA.codigo(), enrutador.evento(TipoEventoPush.SESION_CERRADA, null));

        assertEquals(1, ana1.enviados().size());
        assertEquals(1, ana2.enviados().size());
        assertTrue(luis.enviados().isEmpty());
        assertTrue(!ClienteServidorFalso.JSON.desdeJson(ana1.enviados().get(0), JsonNode.class).has("datos"),
                "los nulos se omiten");
    }

    private CanalGrabador abrir(UsuarioDTO u) {
        var c = new CanalGrabador();
        conexiones.abrirSesion(c, u);
        return c;
    }

    private static MensajeDTO mensaje(String id, Canal canal, String de, String para) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, canal, de, para, LocalDateTime.of(2026, 10, 13, 10, 0),
                "hash", "Hola", 4, 1, null, List.of());
    }

    /** Vista de prueba que graba lo que recibe. */
    static final class VistaGrabadora implements IObservadorServidor {
        final List<MensajeDTO> mensajes = new ArrayList<>();
        final List<EventoLogDTO> eventos = new ArrayList<>();
        final List<List<SesionDTO>> sesiones = new ArrayList<>();

        @Override
        public void onMensaje(MensajeDTO m) {
            mensajes.add(m);
        }

        @Override
        public void onEvento(EventoLogDTO e) {
            eventos.add(e);
        }

        @Override
        public void onSesiones(List<SesionDTO> l) {
            sesiones.add(l);
        }
    }
}
