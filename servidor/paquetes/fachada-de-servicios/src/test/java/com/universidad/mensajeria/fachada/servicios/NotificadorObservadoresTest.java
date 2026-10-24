package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.fachada.servicios.EnrutadorMensajesTest.VistaGrabadora;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificadorObservadoresTest {

    private static final EventoLogDTO EVENTO =
            new EventoLogDTO("e1", TipoEvento.CONEXION, "CONEXION", null, "10.0.0.5", LocalDateTime.now());

    @Test
    void avisaATodosLosObservadores() {
        var notificador = new NotificadorObservadores();
        var a = new VistaGrabadora();
        var b = new VistaGrabadora();
        notificador.suscribir(a);
        notificador.suscribir(b);

        notificador.evento(EVENTO);
        notificador.sesiones(List.of());

        assertEquals(List.of(EVENTO), a.eventos);
        assertEquals(List.of(EVENTO), b.eventos);
        assertEquals(1, a.sesiones.size());
    }

    @Test
    void unObservadorQueFallaNoImpideAvisarALosDemas() {
        var notificador = new NotificadorObservadores();
        var bien = new VistaGrabadora();
        notificador.suscribir(new ObservadorRoto());
        notificador.suscribir(bien);

        notificador.evento(EVENTO);

        assertEquals(List.of(EVENTO), bien.eventos);
    }

    @Test
    void suscribirDosVecesNoDuplicaLosAvisos() {
        var notificador = new NotificadorObservadores();
        var vista = new VistaGrabadora();
        notificador.suscribir(vista);
        notificador.suscribir(vista);

        notificador.evento(EVENTO);

        assertEquals(1, vista.eventos.size());
    }

    @Test
    void lasSesionesSeEntreganComoCopia() {
        var notificador = new NotificadorObservadores();
        var vista = new VistaGrabadora();
        notificador.suscribir(vista);
        var original = new ArrayList<SesionDTO>();

        notificador.sesiones(original);
        original.add(new SesionDTO("s1", "20201001", "Ana Pérez", "10.0.0.5", LocalDateTime.now()));

        assertEquals(List.of(), vista.sesiones.get(0));
    }

    @Test
    void excepcionNegocioNoAdmiteElCodigoOk() {
        assertThrows(IllegalArgumentException.class, () -> new ExcepcionNegocio(CodigoError.OK, "x"));
        assertEquals(CodigoError.COLA_LLENA, new ExcepcionNegocio(CodigoError.COLA_LLENA, "llena").getCodigo());
    }

    /** Observador que siempre falla. */
    private static final class ObservadorRoto implements IObservadorServidor {
        @Override
        public void onMensaje(MensajeDTO m) {
            throw new IllegalStateException("roto");
        }

        @Override
        public void onEvento(EventoLogDTO e) {
            throw new IllegalStateException("roto");
        }

        @Override
        public void onSesiones(List<SesionDTO> l) {
            throw new IllegalStateException("roto");
        }
    }
}
