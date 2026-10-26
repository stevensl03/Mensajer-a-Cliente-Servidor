package com.universidad.mensajeria.cliente.servicios.informes;

import com.universidad.mensajeria.cliente.servicios.ProtocoloFalso;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoInforme;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioInformesTest {

    private final ProtocoloFalso protocolo = new ProtocoloFalso();
    private final ServicioInformes informes = new ServicioInformes(protocolo, new SerializadorJSON());

    @Test
    void enviaElTipoYSoloLosFiltrosConValor() throws Exception {
        protocolo.responder(TipoComando.INFORME, ProtocoloFalso.exito(
                "{\"tipo\":\"LOGS\",\"generado\":\"2026-10-25T20:00:00.123456789\",\"filas\":[]}"));
        var filtros = new HashMap<String, String>();
        filtros.put("tipoEvento", "ERROR");
        filtros.put("usuario", "  ");
        filtros.put("desde", null);
        filtros.put("hasta", " 2026-10-25T23:59:59.999999999 ");

        informes.obtener(TipoInforme.LOGS, filtros).get(5, TimeUnit.SECONDS);

        assertEquals(Map.of("tipo", "LOGS", "tipoEvento", "ERROR", "hasta", "2026-10-25T23:59:59.999999999"),
                protocolo.siguiente().params());
    }

    @Test
    void lasFilasSonMapasYElJsonLlegaConSangria() throws Exception {
        protocolo.responder(TipoComando.INFORME, ProtocoloFalso.exito("""
                {"tipo":"USUARIOS_CONECTADOS","generado":"2026-10-25T20:00:00","generadoPor":"20201001",
                 "filas":[{"codigo":"20201003","nombreCompleto":"María Rodríguez","cantidadSesiones":1}]}"""));

        InformeRecibido r = informes.obtener(TipoInforme.USUARIOS_CONECTADOS, null).get(5, TimeUnit.SECONDS);

        var fila = assertInstanceOf(Map.class, r.informe().filas().get(0));
        assertEquals("María Rodríguez", fila.get("nombreCompleto"));
        assertEquals(1, fila.get("cantidadSesiones"));
        assertEquals("20201001", r.informe().generadoPor());
        assertTrue(r.json().contains("\n") && r.json().contains("\"cantidadSesiones\" : 1"), r.json());
    }

    @Test
    void unFiltroInvalidoLlegaComoExcepcionDelServidor() {
        protocolo.responder(TipoComando.INFORME, ProtocoloFalso.error(CodigoError.PETICION_INVALIDA, "Filtro inválido"));

        var e = assertThrows(ExecutionException.class,
                () -> informes.obtener(TipoInforme.LOGS, Map.of("desde", "ayer")).get(5, TimeUnit.SECONDS));

        var ex = assertInstanceOf(ExcepcionServidor.class, e.getCause());
        assertEquals(CodigoError.PETICION_INVALIDA, ex.codigo());
        assertEquals("Filtro inválido", ex.getMessage());
    }
}
