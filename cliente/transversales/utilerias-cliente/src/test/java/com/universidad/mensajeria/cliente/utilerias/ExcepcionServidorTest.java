package com.universidad.mensajeria.cliente.utilerias;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.RespuestaDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcepcionServidorTest {

    @Test
    void exigirExitoDevuelveLaRespuestaExitosa() {
        var r = new RespuestaDTO("p", true, CodigoError.OK, "OK", null);

        assertSame(r, ExcepcionServidor.exigirExito(r));
    }

    @Test
    void exigirExitoLanzaConElCodigoYElMensajeDelServidor() {
        var r = new RespuestaDTO("p", false, CodigoError.CUPO_LLENO, "No hay cupos libres en el servidor", null);

        var e = assertThrows(ExcepcionServidor.class, () -> ExcepcionServidor.exigirExito(r));

        assertEquals(CodigoError.CUPO_LLENO, e.codigo());
        assertEquals("No hay cupos libres en el servidor", e.getMessage());
    }

    @Test
    void sinMensajeUsaElNombreDelCodigo() {
        var r = new RespuestaDTO("p", false, CodigoError.ERROR_INTERNO, null, null);

        assertEquals("ERROR_INTERNO", assertThrows(ExcepcionServidor.class, () -> ExcepcionServidor.exigirExito(r)).getMessage());
    }
}
