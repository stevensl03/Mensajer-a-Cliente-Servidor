package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroLogsTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 13, 10, 42, 5);
    private static final EventoLogDTO EVENTO =
            new EventoLogDTO("e1", TipoEvento.LOGIN_OK, "LOGIN_OK sesion=s1", "20201001", "127.0.0.1", HORA);

    @Test
    void filtroVacioAceptaTodo() {
        assertTrue(new FiltroLogs(null, null, null, null).acepta(EVENTO));
    }

    @Test
    void filtraPorTipo() {
        assertTrue(new FiltroLogs(TipoEvento.LOGIN_OK, null, null, null).acepta(EVENTO));
        assertFalse(new FiltroLogs(TipoEvento.ERROR, null, null, null).acepta(EVENTO));
    }

    @Test
    void filtraPorUsuario() {
        assertTrue(new FiltroLogs(null, "20201001", null, null).acepta(EVENTO));
        assertFalse(new FiltroLogs(null, "20201002", null, null).acepta(EVENTO));

        var sinUsuario = new EventoLogDTO("e2", TipoEvento.CONEXION, "CONEXION", null, "127.0.0.1", HORA);
        assertFalse(new FiltroLogs(null, "20201001", null, null).acepta(sinUsuario));
    }

    @Test
    void elRangoDeFechasEsInclusivo() {
        assertTrue(new FiltroLogs(null, null, HORA, HORA).acepta(EVENTO));
        assertFalse(new FiltroLogs(null, null, HORA.plusNanos(1), null).acepta(EVENTO));
        assertFalse(new FiltroLogs(null, null, null, HORA.minusNanos(1)).acepta(EVENTO));
    }

    @Test
    void todosLosCamposDebenCumplirse() {
        assertTrue(new FiltroLogs(TipoEvento.LOGIN_OK, "20201001", HORA.minusHours(1), HORA.plusHours(1)).acepta(EVENTO));
        assertFalse(new FiltroLogs(TipoEvento.LOGIN_OK, "20201002", HORA.minusHours(1), HORA.plusHours(1)).acepta(EVENTO));
    }
}
