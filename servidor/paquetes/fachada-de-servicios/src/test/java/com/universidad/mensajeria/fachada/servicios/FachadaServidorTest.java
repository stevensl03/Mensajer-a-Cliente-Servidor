package com.universidad.mensajeria.fachada.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.servicios.EnrutadorMensajesTest.VistaGrabadora;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ADMIN;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ANA;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.LUIS;
import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.MARIA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La fachada ensamblada, conducida como lo haría el transporte (§6.2–§6.6). */
class FachadaServidorTest {

    /** El módulo se ejecuta desde paquetes/fachada-de-servicios. */
    private static final Path CONFIG = Path.of("../../config");
    private static final ContenidoDTO HOLA = new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null);

    private final ConfiguracionServidor cfg =
            ConfiguracionServidor.cargar(CONFIG.resolve("servidor.properties"), CONFIG.resolve("bd.properties"));
    private final ProtocoloFalso protocolo = new ProtocoloFalso();
    private final GestorMensajesFalso gestor = new GestorMensajesFalso();
    private final AlmacenMensajesFalso almacen = new AlmacenMensajesFalso();
    private final LogGrabador log = new LogGrabador();
    private final GestorConexiones conexiones = new GestorConexiones(cfg.maxSesiones());
    private final VistaGrabadora vista = new VistaGrabadora();
    private final FachadaServidor fachada = new FachadaServidor(protocolo, new ClienteServidorFalso(),
            new ServiciosFalso(), new UsuariosFalso(), gestor, almacen, new FabricaMensajes(), log, conexiones,
            ClienteServidorFalso.JSON, cfg);
    private final IReceptorConexiones receptor;

    FachadaServidorTest() {
        fachada.suscribir(vista);
        fachada.iniciarServidor();
        receptor = protocolo.receptor;
        gestor.sincrona = true;
    }

    @Test
    void iniciarArrancaLaColaYElTransporteConLaConfiguracion() {
        assertEquals(cfg.hilosConsumidores(), gestor.hilos);
        assertTrue(gestor.consumidor instanceof ProcesadorMensajes);
        assertEquals(cfg.puerto(), protocolo.puerto);
        assertTrue(protocolo.activo);
        assertEquals("192.168.1.10", fachada.getIpServidor());
    }

    @Test
    void alConectarRegistraYEnviaInfoServidorSinOcuparCupo() {
        var canal = new CanalGrabador();

        receptor.alConectar(canal);

        assertEquals(List.of(TipoEvento.CONEXION), log.tipos());
        JsonNode info = push(canal, "INFO_SERVIDOR").get(0);
        assertEquals("192.168.1.10", info.get("ipServidor").asText());
        assertEquals(cfg.puerto(), info.get("puerto").asInt());
        assertEquals("TCP", info.get("protocolo").asText());
        assertEquals(cfg.maxSesiones(), info.get("cuposLibres").asInt());
    }

    @Test
    void loginCorrectoRespondeConElIdDelCanal() {
        var canal = new CanalGrabador();

        JsonNode r = pedir(canal, "p1", TipoComando.LOGIN, Map.of("codigo", ANA.codigo(), "clave", UsuariosFalso.clave(ANA)), null);

        assertTrue(r.get("exito").asBoolean());
        assertEquals("p1", r.get("idPeticion").asText());
        assertEquals(canal.getId(), r.get("datos").get("idSesion").asText());
        assertTrue(canal.estaAbierto());
    }

    @Test
    void credencialesInvalidasRespondenYElCanalSigueAbierto() {
        var canal = new CanalGrabador();

        JsonNode r = pedir(canal, "p1", TipoComando.LOGIN, Map.of("codigo", ANA.codigo(), "clave", "mala"), null);

        assertEquals("CREDENCIALES_INVALIDAS", r.get("codigo").asText());
        assertTrue(canal.estaAbierto());
    }

    @Test
    void conElCupoLlenoSeRespondeYDespuesSeCierraElCanal() {
        for (int i = 0; i < cfg.maxSesiones(); i++) {
            login(new CanalGrabador(), ANA);
        }
        var canal = new CanalGrabador();

        JsonNode r = pedir(canal, "p1", TipoComando.LOGIN, Map.of("codigo", LUIS.codigo(), "clave", UsuariosFalso.clave(LUIS)), null);

        assertEquals("CUPO_LLENO", r.get("codigo").asText());
        assertFalse(canal.estaAbierto(), "se cierra después de responder");
    }

    @Test
    void jsonMalFormadoRespondePeticionInvalidaSinCerrar() {
        var canal = new CanalGrabador();

        receptor.alRecibir(canal, "{esto no es json");

        JsonNode r = ultima(canal);
        assertEquals("PETICION_INVALIDA", r.get("codigo").asText());
        assertTrue(canal.estaAbierto());
    }

    @Test
    void comandoSinSesionRespondeNoAutenticado() {
        JsonNode r = pedir(new CanalGrabador(), "p1", TipoComando.ENVIAR_FORO, null, HOLA);

        assertEquals("NO_AUTENTICADO", r.get("codigo").asText());
        assertTrue(gestor.encolados.isEmpty());
    }

    @Test
    void foroLlegaATodosYPrivadoAlDestinatarioYAlRemitente() {
        var ana = login(new CanalGrabador(), ANA);
        var luis = login(new CanalGrabador(), LUIS);
        var maria = login(new CanalGrabador(), MARIA);

        JsonNode r = pedir(ana, "p1", TipoComando.ENVIAR_FORO, null, HOLA);
        assertEquals("ENCOLADO", r.get("datos").get("estado").asText());
        for (CanalGrabador c : List.of(ana, luis, maria)) {
            assertEquals(1, push(c, "MENSAJE_NUEVO").size());
        }

        pedir(ana, "p2", TipoComando.ENVIAR_PRIVADO, Map.of("destino", LUIS.codigo()), HOLA);
        assertEquals(2, push(ana, "MENSAJE_NUEVO").size());
        assertEquals(2, push(luis, "MENSAJE_NUEVO").size());
        assertEquals(1, push(maria, "MENSAJE_NUEVO").size());
        JsonNode privado = push(luis, "MENSAJE_NUEVO").get(1).get("mensaje");
        assertEquals(ANA.codigo(), privado.get("remitente").asText(), "el remitente sale de la sesión");
        assertEquals(List.of(TipoEvento.MENSAJE_FORO, TipoEvento.MENSAJE_PRIVADO),
                log.tipos().stream().filter(t -> t.name().startsWith("MENSAJE")).toList());
    }

    @Test
    void unMensajeAlAdminLlegaALasVistas() {
        var luis = login(new CanalGrabador(), LUIS);

        pedir(luis, "p1", TipoComando.ENVIAR_PRIVADO, Map.of("destino", ADMIN.codigo()), HOLA);

        assertEquals(1, vista.mensajes.size());
        assertEquals(ADMIN.codigo(), vista.mensajes.get(0).destinatario());
    }

    @Test
    void elAdminPublicaEnElForoYEscribeEnPrivado() {
        var ana = login(new CanalGrabador(), ANA);
        var luis = login(new CanalGrabador(), LUIS);

        fachada.publicarEnForo(HOLA);
        fachada.enviarPrivado(ANA.codigo(), HOLA);

        assertEquals(ADMIN.codigo(), push(luis, "MENSAJE_NUEVO").get(0).get("mensaje").get("remitente").asText());
        assertEquals(2, push(ana, "MENSAJE_NUEVO").size());
        assertEquals(1, push(luis, "MENSAJE_NUEVO").size());
        assertNull(gestor.encolados.get(0).idSesion());
        assertThrows(ExcepcionNegocio.class, () -> fachada.enviarPrivado("99999999", HOLA));
        assertThrows(ExcepcionNegocio.class, () -> fachada.enviarPrivado(ADMIN.codigo(), HOLA));
        assertThrows(ExcepcionNegocio.class,
                () -> fachada.publicarEnForo(new ContenidoDTO(TipoMensaje.TEXTO, "  ", null, null)));
    }

    @Test
    void losCuatroInformes() {
        var ana = login(new CanalGrabador(), ANA);
        login(new CanalGrabador(), ANA);
        login(new CanalGrabador(), LUIS);
        pedir(ana, "p1", TipoComando.ENVIAR_FORO, null, HOLA);

        JsonNode registrados = informe(TipoInforme.USUARIOS_REGISTRADOS, Map.of());
        JsonNode conectados = informe(TipoInforme.USUARIOS_CONECTADOS, Map.of());
        JsonNode mensajes = informe(TipoInforme.MENSAJES, Map.of());
        JsonNode logs = informe(TipoInforme.LOGS, Map.of("tipoEvento", "LOGIN_OK", "usuario", ANA.codigo()));

        assertEquals(4, registrados.get("filas").size());
        assertFalse(registrados.get("filas").get(0).has("contrasenaHash"));
        assertEquals(ADMIN.codigo(), registrados.get("generadoPor").asText());
        assertEquals(2, conectados.get("filas").size());
        assertEquals(2, conectados.get("filas").get(0).get("cantidadSesiones").asInt());
        assertEquals(1, mensajes.get("filas").size());
        assertEquals(2, logs.get("filas").size());
        assertThrows(ExcepcionNegocio.class, () -> fachada.informe(TipoInforme.LOGS, Map.of("desde", "ayer")));
    }

    @Test
    void elComandoInformeLoFirmaQuienLoPide() {
        var luis = login(new CanalGrabador(), LUIS);

        JsonNode r = pedir(luis, "p1", TipoComando.INFORME, Map.of("tipo", "USUARIOS_CONECTADOS"), null);

        assertEquals(LUIS.codigo(), r.get("datos").get("generadoPor").asText());
        assertEquals("PETICION_INVALIDA",
                pedir(luis, "p2", TipoComando.INFORME, Map.of("tipo", "VENTAS"), null).get("codigo").asText());
    }

    @Test
    void elCierrePorElAdminAvisaCierraLaSesionYElCanal() {
        var ana = login(new CanalGrabador(), ANA);
        var luis = login(new CanalGrabador(), LUIS);

        fachada.cerrarSesion(ana.getId());

        assertEquals("CERRADA_POR_ADMIN", push(ana, "SESION_CERRADA").get(0).get("motivo").asText());
        assertFalse(ana.estaAbierto());
        assertTrue(log.desconexiones.contains(ana.getId() + ":CERRADA_POR_ADMIN"));
        assertEquals(1, fachada.listarSesiones().size());
        assertEquals(1, push(luis, "USUARIO_DESCONECTADO").size());
        receptor.alDesconectar(ana);
        assertEquals(1, log.desconexiones.size(), "el transporte avisa después, pero no se cierra dos veces");
        assertThrows(ExcepcionNegocio.class, () -> fachada.cerrarSesion(ana.getId()));
    }

    @Test
    void logoutRespondeYCierraElCanal() {
        var ana = login(new CanalGrabador(), ANA);

        JsonNode r = pedir(ana, "p1", TipoComando.LOGOUT, null, null);

        assertTrue(r.get("exito").asBoolean());
        assertFalse(ana.estaAbierto());
        assertEquals(List.of(ana.getId() + ":LOGOUT"), log.desconexiones);
    }

    @Test
    void desconectarseCierraLaSesionComoDesconexion() {
        var ana = login(new CanalGrabador(), ANA);

        receptor.alDesconectar(ana);
        receptor.alDesconectar(new CanalGrabador());

        assertEquals(List.of(ana.getId() + ":DESCONEXION"), log.desconexiones);
        assertEquals(cfg.maxSesiones(), conexiones.cuposLibres());
    }

    @Test
    void lasVistasRecibenCadaEventoDelLog() {
        var canal = new CanalGrabador();
        receptor.alConectar(canal);
        login(canal, ANA);

        assertEquals(List.of(TipoEvento.CONEXION, TipoEvento.LOGIN_OK),
                vista.eventos.stream().map(e -> e.tipo()).toList());
    }

    @Test
    void listarUsuariosMuestraAlAdminConectado() {
        login(new CanalGrabador(), ANA);

        var estados = fachada.listarUsuarios();

        assertEquals(4, estados.size());
        assertTrue(estados.stream().filter(e -> e.usuario().equals(ADMIN)).findFirst().orElseThrow().conectado());
        assertEquals(1, estados.stream().filter(e -> e.usuario().equals(ANA)).findFirst().orElseThrow().sesiones());
    }

    @Test
    void apagadoOrdenadoAvisaCierraTodoYEsIdempotente() {
        var ana = login(new CanalGrabador(), ANA);
        var luis = login(new CanalGrabador(), LUIS);

        fachada.detenerServidor();
        fachada.detenerServidor();

        assertEquals("SERVIDOR_DETENIDO", push(ana, "SESION_CERRADA").get(0).get("motivo").asText());
        assertEquals(1, push(luis, "SESION_CERRADA").size());
        assertEquals(1, protocolo.detenciones);
        assertTrue(gestor.detenido);
        assertTrue(fachada.listarSesiones().isEmpty());
        assertTrue(log.desconexiones.stream().allMatch(d -> d.endsWith(":SERVIDOR_DETENIDO")));
        assertEquals(2, log.desconexiones.size());
    }

    private CanalGrabador login(CanalGrabador c, UsuarioDTO u) {
        JsonNode r = pedir(c, "login", TipoComando.LOGIN, Map.of("codigo", u.codigo(), "clave", UsuariosFalso.clave(u)), null);
        assertTrue(r.get("exito").asBoolean(), r.toString());
        return c;
    }

    private JsonNode pedir(CanalGrabador c, String id, TipoComando comando, Map<String, String> parametros,
                           ContenidoDTO contenido) {
        receptor.alRecibir(c, ClienteServidorFalso.JSON.aJson(new PeticionDTO(id, comando, parametros, contenido)));
        return ultima(c);
    }

    /** @return la última respuesta (trae idPeticion) que recibió el canal. */
    private static JsonNode ultima(CanalGrabador c) {
        List<JsonNode> respuestas = tramas(c).stream().filter(t -> t.has("exito")).toList();
        return respuestas.get(respuestas.size() - 1);
    }

    /** @return los {@code datos} de los push de ese tipo que recibió el canal. */
    private static List<JsonNode> push(CanalGrabador c, String evento) {
        return tramas(c).stream()
                .filter(t -> t.has("evento") && t.get("evento").asText().equals(evento))
                .map(t -> t.get("datos"))
                .toList();
    }

    private static List<JsonNode> tramas(CanalGrabador c) {
        var l = new ArrayList<JsonNode>();
        c.enviados().forEach(t -> l.add(ClienteServidorFalso.JSON.desdeJson(t, JsonNode.class)));
        return l;
    }

    private JsonNode informe(TipoInforme t, Map<String, String> filtros) {
        return ClienteServidorFalso.JSON.desdeJson(fachada.informe(t, filtros), JsonNode.class);
    }
}
