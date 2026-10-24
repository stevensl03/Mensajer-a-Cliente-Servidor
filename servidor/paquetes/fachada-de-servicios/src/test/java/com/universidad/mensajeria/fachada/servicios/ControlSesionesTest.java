package com.universidad.mensajeria.fachada.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.servicios.EnrutadorMensajesTest.VistaGrabadora;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ANA;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.LUIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlSesionesTest {

    private final GestorConexiones conexiones = new GestorConexiones(2);
    private final LogGrabador log = new LogGrabador();
    private final NotificadorObservadores notificador = new NotificadorObservadores();
    private final VistaGrabadora vista = new VistaGrabadora();
    private final ControlSesiones sesiones;

    ControlSesionesTest() {
        var enrutador = new EnrutadorMensajes(conexiones, new ClienteServidorFalso(), notificador,
                ClienteServidorFalso.JSON);
        sesiones = new ControlSesiones(new UsuariosFalso(), conexiones, log, enrutador, notificador);
        notificador.suscribir(vista);
    }

    @Test
    void loginCorrectoAbreLaSesionRegistraYAvisa() {
        var yaConectado = new CanalGrabador();
        sesiones.abrir(yaConectado, LUIS.codigo(), UsuariosFalso.clave(LUIS));
        var canal = new CanalGrabador();

        UsuarioDTO u = sesiones.abrir(canal, ANA.codigo(), UsuariosFalso.clave(ANA));

        assertEquals(ANA, u);
        assertEquals(ANA, conexiones.usuarioDe(canal.getId()).orElseThrow());
        assertEquals(canal.getId(), log.conexiones.get(1).idSesion());
        assertEquals(List.of(TipoEvento.LOGIN_OK, TipoEvento.LOGIN_OK), log.tipos());
        JsonNode push = ultimoPush(yaConectado);
        assertEquals("USUARIO_CONECTADO", push.get("evento").asText());
        assertEquals(ANA.codigo(), push.get("datos").get("codigo").asText());
        assertEquals("Ana Pérez", push.get("datos").get("nombreCompleto").asText());
        assertEquals(1, push.get("datos").get("sesiones").asInt());
        assertEquals(1, canal.enviados().size(), "el nuevo también recibe el aviso");
        assertEquals(2, vista.sesiones.get(1).size());
    }

    @Test
    void credencialesInvalidasNoAbrenSesion() {
        var canal = new CanalGrabador();

        var e = assertThrows(ExcepcionNegocio.class, () -> sesiones.abrir(canal, ANA.codigo(), "mala"));

        assertEquals(CodigoError.CREDENCIALES_INVALIDAS, e.getCodigo());
        assertEquals(List.of(TipoEvento.LOGIN_FALLIDO), log.tipos());
        assertTrue(!log.eventos.get(0).descripcion().contains("mala"), "nunca se registra la contraseña");
        assertEquals(2, conexiones.cuposLibres());
        assertTrue(vista.sesiones.isEmpty());
    }

    @Test
    void unCanalConSesionNoPuedeHacerLoginOtraVez() {
        var canal = new CanalGrabador();
        sesiones.abrir(canal, ANA.codigo(), UsuariosFalso.clave(ANA));

        var e = assertThrows(ExcepcionNegocio.class,
                () -> sesiones.abrir(canal, LUIS.codigo(), UsuariosFalso.clave(LUIS)));

        assertEquals(CodigoError.PETICION_INVALIDA, e.getCodigo());
        assertEquals(1, conexiones.cuposLibres());
        assertEquals(ANA, conexiones.usuarioDe(canal.getId()).orElseThrow());
    }

    @Test
    void conElCupoLlenoSeRechazaYSeRegistra() {
        sesiones.abrir(new CanalGrabador(), ANA.codigo(), UsuariosFalso.clave(ANA));
        sesiones.abrir(new CanalGrabador(), ANA.codigo(), UsuariosFalso.clave(ANA));

        var e = assertThrows(ExcepcionNegocio.class,
                () -> sesiones.abrir(new CanalGrabador(), LUIS.codigo(), UsuariosFalso.clave(LUIS)));

        assertEquals(CodigoError.CUPO_LLENO, e.getCodigo());
        assertEquals(TipoEvento.CUPO_LLENO, log.tipos().get(2));
        assertTrue(conexiones.canalesDe(LUIS.codigo()).isEmpty());
    }

    @Test
    void cerrarLiberaElCupoRegistraElMotivoYAvisa() {
        var a1 = new CanalGrabador();
        var a2 = new CanalGrabador();
        sesiones.abrir(a1, ANA.codigo(), UsuariosFalso.clave(ANA));
        sesiones.abrir(a2, ANA.codigo(), UsuariosFalso.clave(ANA));

        sesiones.cerrar(a1.getId(), "LOGOUT");

        assertEquals(1, conexiones.cuposLibres());
        assertEquals(List.of(a1.getId() + ":LOGOUT"), log.desconexiones);
        JsonNode push = ultimoPush(a2);
        assertEquals("USUARIO_DESCONECTADO", push.get("evento").asText());
        assertEquals(ANA.codigo(), push.get("datos").get("codigo").asText());
        assertEquals(1, push.get("datos").get("sesiones").asInt(), "le queda una sesión");
        assertEquals(1, vista.sesiones.get(vista.sesiones.size() - 1).size());
    }

    @Test
    void cerrarDosVecesSoloActuaLaPrimera() {
        var canal = new CanalGrabador();
        sesiones.abrir(canal, ANA.codigo(), UsuariosFalso.clave(ANA));

        sesiones.cerrar(canal.getId(), "DESCONEXION");
        sesiones.cerrar(canal.getId(), "DESCONEXION");

        assertEquals(1, log.desconexiones.size());
        assertEquals(2, conexiones.cuposLibres());
    }

    private static JsonNode ultimoPush(CanalGrabador c) {
        List<String> enviados = c.enviados();
        return ClienteServidorFalso.JSON.desdeJson(enviados.get(enviados.size() - 1), JsonNode.class);
    }
}
