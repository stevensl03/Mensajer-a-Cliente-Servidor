package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.fachada.servicios.comandos.DespachadorComandos;
import com.universidad.mensajeria.fachada.servicios.comandos.IManejadorComando;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorLogin;
import com.universidad.mensajeria.fachada.servicios.comandos.ManejadorLogout;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.universidad.mensajeria.fachada.servicios.UsuariosFalso.ANA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DespachadorComandos con los manejadores de LOGIN y LOGOUT. */
class ComandosSesionTest {

    private final GestorConexiones conexiones = new GestorConexiones(5);
    private final ClienteServidorFalso clienteServidor = new ClienteServidorFalso();
    private final ControlSesiones sesiones;
    private final EnumMap<TipoComando, IManejadorComando> manejadores = new EnumMap<>(TipoComando.class);
    private final AtomicInteger ejecucionesListar = new AtomicInteger();

    ComandosSesionTest() {
        var notificador = new NotificadorObservadores();
        var enrutador = new EnrutadorMensajes(conexiones, clienteServidor, notificador, ClienteServidorFalso.JSON);
        sesiones = new ControlSesiones(new UsuariosFalso(), conexiones, new LogGrabador(), enrutador, notificador);
        manejadores.put(TipoComando.LOGIN, new ManejadorLogin(sesiones, clienteServidor, () -> "192.168.1.10"));
        manejadores.put(TipoComando.LOGOUT, new ManejadorLogout(sesiones, clienteServidor));
        manejadores.put(TipoComando.LISTAR_USUARIOS, (p, c) -> {
            ejecucionesListar.incrementAndGet();
            return clienteServidor.exito(p.idPeticion(), null);
        });
    }

    @Test
    void loginRespondeUsuarioIdSesionEIpDelServidor() {
        var canal = new CanalGrabador();

        RespuestaDTO r = despachador().despachar(login("p1", ANA.codigo(), UsuariosFalso.clave(ANA)), canal);

        assertTrue(r.exito());
        assertEquals("p1", r.idPeticion());
        assertEquals(canal.getId(), r.datos().get("idSesion").asText(), "idSesion es el id del canal");
        assertEquals(ANA.codigo(), r.datos().get("usuario").get("codigo").asText());
        assertEquals("192.168.1.10", r.datos().get("ipServidor").asText());
        assertFalse(r.datos().get("usuario").has("contrasenaHash"));
    }

    @Test
    void loginConCredencialesInvalidasRespondeSuCodigo() {
        RespuestaDTO r = despachador().despachar(login("p1", ANA.codigo(), "mala"), new CanalGrabador());

        assertFalse(r.exito());
        assertEquals(CodigoError.CREDENCIALES_INVALIDAS, r.codigo());
        assertEquals("p1", r.idPeticion());
    }

    @Test
    void loginSinParametrosEsPeticionInvalida() {
        var p = new PeticionDTO("p1", TipoComando.LOGIN, Map.of("codigo", ANA.codigo()), null);

        assertEquals(CodigoError.PETICION_INVALIDA, despachador().despachar(p, new CanalGrabador()).codigo());
    }

    @Test
    void comandoSinSesionRespondeNoAutenticadoSinEjecutarlo() {
        var p = new PeticionDTO("p2", TipoComando.LISTAR_USUARIOS, null, null);

        RespuestaDTO r = despachador().despachar(p, new CanalGrabador());

        assertEquals(CodigoError.NO_AUTENTICADO, r.codigo());
        assertEquals("p2", r.idPeticion());
        assertEquals(0, ejecucionesListar.get());
    }

    @Test
    void conSesionElComandoSeEjecuta() {
        var canal = new CanalGrabador();
        despachador().despachar(login("p1", ANA.codigo(), UsuariosFalso.clave(ANA)), canal);

        RespuestaDTO r = despachador().despachar(new PeticionDTO("p2", TipoComando.LISTAR_USUARIOS, null, null), canal);

        assertTrue(r.exito());
        assertEquals(1, ejecucionesListar.get());
    }

    @Test
    void logoutCierraLaSesionYResponde() {
        var canal = new CanalGrabador();
        var despachador = despachador();
        despachador.despachar(login("p1", ANA.codigo(), UsuariosFalso.clave(ANA)), canal);

        RespuestaDTO r = despachador.despachar(new PeticionDTO("p2", TipoComando.LOGOUT, null, null), canal);

        assertTrue(r.exito());
        assertTrue(conexiones.usuarioDe(canal.getId()).isEmpty());
        assertEquals(CodigoError.NO_AUTENTICADO,
                despachador.despachar(new PeticionDTO("p3", TipoComando.LOGOUT, null, null), canal).codigo());
    }

    @Test
    void comandoSinManejadorEsPeticionInvalida() {
        var p = new PeticionDTO("p1", TipoComando.INFORME, null, null);

        assertEquals(CodigoError.PETICION_INVALIDA, despachador().despachar(p, new CanalGrabador()).codigo());
    }

    @Test
    void unErrorInesperadoSeRespondeComoErrorInterno() {
        manejadores.put(TipoComando.LOGIN, (p, c) -> {
            throw new IllegalStateException("fallo inesperado");
        });

        RespuestaDTO r = despachador().despachar(login("p1", ANA.codigo(), "x"), new CanalGrabador());

        assertEquals(CodigoError.ERROR_INTERNO, r.codigo());
        assertFalse(r.mensaje().contains("fallo inesperado"), "no se filtran detalles internos al cliente");
    }

    private DespachadorComandos despachador() {
        return new DespachadorComandos(manejadores, conexiones);
    }

    private static PeticionDTO login(String id, String codigo, String clave) {
        return new PeticionDTO(id, TipoComando.LOGIN, Map.of("codigo", codigo, "clave", clave), null);
    }
}
