package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEventosTest {

    private static final SesionDTO SESION =
            new SesionDTO("s1", "20201001", "Ana Pérez", "10.0.0.5", LocalDateTime.of(2026, 10, 13, 10, 0));

    private final AlmacenFalso almacen = new AlmacenFalso();
    private final LogEventos log = new LogEventos(almacen);

    @Test
    void registrarPersisteEIndexa() {
        log.registrar(TipoEvento.CONEXION, "CONEXION ip=10.0.0.5", null, "10.0.0.5");

        EventoLogDTO e = log.todos().get(0);
        assertEquals(TipoEvento.CONEXION, e.tipo());
        assertEquals("CONEXION ip=10.0.0.5", e.descripcion());
        assertNull(e.usuario());
        assertEquals("10.0.0.5", e.ip());
        assertEquals(List.of(e), almacen.logs);
    }

    @Test
    void consultarPorTipoUsuarioYRango() {
        log.registrar(TipoEvento.LOGIN_FALLIDO, "LOGIN_FALLIDO", "20201001", "10.0.0.5");
        log.registrar(TipoEvento.LOGIN_OK, "LOGIN_OK", "20201001", "10.0.0.5");
        log.registrar(TipoEvento.LOGIN_OK, "LOGIN_OK", "20201002", "10.0.0.6");
        log.registrar(TipoEvento.ERROR, "ERROR", null, null);

        assertEquals(2, log.consultar(new FiltroLogs(TipoEvento.LOGIN_OK, null, null, null)).size());
        assertEquals(2, log.consultar(new FiltroLogs(null, "20201001", null, null)).size());
        assertEquals(1, log.consultar(new FiltroLogs(TipoEvento.LOGIN_OK, "20201002", null, null)).size());
        assertTrue(log.consultar(new FiltroLogs(TipoEvento.CUPO_LLENO, null, null, null)).isEmpty());
        assertTrue(log.consultar(new FiltroLogs(null, "nadie", null, null)).isEmpty());
        assertEquals(4, log.consultar(new FiltroLogs(null, null, null, null)).size());
        assertTrue(log.consultar(new FiltroLogs(null, null, LocalDateTime.now().plusDays(1), null)).isEmpty());
    }

    @Test
    void consultarConservaElOrdenDeLlegada() {
        log.registrar(TipoEvento.LOGIN_OK, "primero", "20201001", null);
        log.registrar(TipoEvento.LOGIN_OK, "segundo", "20201001", null);

        List<EventoLogDTO> r = log.consultar(new FiltroLogs(null, "20201001", null, null));

        assertEquals(List.of("primero", "segundo"), r.stream().map(EventoLogDTO::descripcion).toList());
    }

    @Test
    void conexionRegistraLaFilaYElEventoLoginOk() {
        log.conexion(SESION);

        assertEquals(List.of(SESION), almacen.conexiones);
        EventoLogDTO e = log.todos().get(0);
        assertEquals(TipoEvento.LOGIN_OK, e.tipo());
        assertEquals("LOGIN_OK sesion=s1", e.descripcion());
        assertEquals("20201001", e.usuario());
        assertEquals("10.0.0.5", e.ip());
    }

    @Test
    void desconexionCierraLaFilaYRegistraElMotivo() {
        log.desconexion(SESION, "LOGOUT");

        assertEquals(List.of("s1"), almacen.desconexiones);
        EventoLogDTO e = log.todos().get(0);
        assertEquals(TipoEvento.DESCONEXION, e.tipo());
        assertEquals("DESCONEXION sesion=s1 motivo=LOGOUT", e.descripcion());
        assertEquals("20201001", e.usuario());
    }

    @Test
    void cargarHistorialIndexaEnOrdenSinVolverAPersistir() {
        var antes = new EventoLogDTO("a", TipoEvento.ERROR, "antes", null, null, LocalDateTime.of(2026, 10, 1, 8, 0));
        var despues = new EventoLogDTO("b", TipoEvento.LOGIN_OK, "despues", "20201001", null,
                LocalDateTime.of(2026, 10, 2, 8, 0));
        almacen.historial.addAll(List.of(despues, antes));

        log.cargarHistorial();

        assertEquals(List.of(antes, despues), log.todos());
        assertEquals(List.of(despues), log.consultar(new FiltroLogs(null, "20201001", null, null)));
        assertTrue(almacen.logs.isEmpty());
    }

    @Test
    void siLaBdFallaElEventoQuedaEnMemoria() {
        almacen.fallarAlGuardarLogs = true;

        log.registrar(TipoEvento.ERROR, "ERROR id=m1", null, null);

        assertEquals(1, log.todos().size());
        assertEquals(1, log.consultar(new FiltroLogs(TipoEvento.ERROR, null, null, null)).size());
    }

    @Test
    void siLaBdFallaLaConexionYLaDesconexionNoInterrumpenElCasoDeUso() {
        almacen.fallarAlRegistrarConexiones = true;

        log.conexion(SESION);
        log.desconexion(SESION, "LOGOUT");

        assertEquals(List.of(TipoEvento.LOGIN_OK, TipoEvento.DESCONEXION),
                log.todos().stream().map(EventoLogDTO::tipo).toList());
    }

    @Test
    void todosDevuelveUnaCopiaInmutable() {
        log.registrar(TipoEvento.CONEXION, "CONEXION", null, null);
        List<EventoLogDTO> copia = log.todos();

        log.registrar(TipoEvento.CONEXION, "CONEXION", null, null);

        assertEquals(1, copia.size());
    }

    /** IAlmacenarInformacion sin BD que graba lo que recibe. */
    private static final class AlmacenFalso implements IAlmacenarInformacion {
        final List<EventoLogDTO> logs = new ArrayList<>();
        final List<EventoLogDTO> historial = new ArrayList<>();
        final List<SesionDTO> conexiones = new ArrayList<>();
        final List<String> desconexiones = new ArrayList<>();
        boolean fallarAlGuardarLogs;
        boolean fallarAlRegistrarConexiones;

        @Override
        public void guardarLog(EventoLogDTO e) {
            if (fallarAlGuardarLogs) {
                throw new IllegalStateException("BD caída");
            }
            logs.add(e);
        }

        @Override
        public List<EventoLogDTO> historialLogs() {
            return List.copyOf(historial);
        }

        @Override
        public void registrarConexion(SesionDTO s) {
            if (fallarAlRegistrarConexiones) {
                throw new IllegalStateException("BD caída");
            }
            conexiones.add(s);
        }

        @Override
        public void registrarDesconexion(String idSesion, LocalDateTime fin) {
            if (fallarAlRegistrarConexiones) {
                throw new IllegalStateException("BD caída");
            }
            desconexiones.add(idSesion);
        }

        @Override
        public void inicializar() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UsuarioRegistroDTO> usuariosRegistrados() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MensajeDTO guardarMensaje(MensajeDTO m, List<ImagenProcesadaDTO> imgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> leerImagen(String idMensaje, TipoFiltro etapa) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MensajeDTO> historialMensajes() {
            throw new UnsupportedOperationException();
        }
    }
}
