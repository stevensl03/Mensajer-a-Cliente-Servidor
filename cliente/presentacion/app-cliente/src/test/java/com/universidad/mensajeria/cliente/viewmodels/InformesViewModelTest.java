package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.TipoInforme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sin JavaFX: ejecutores directos (D-10) y reloj fijo en el 25-oct-2026. */
class InformesViewModelTest {

    private static final Clock RELOJ = Clock.fixed(
            LocalDateTime.of(2026, 10, 25, 21, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private FachadaFalsa fachada;
    private Map<TipoInforme, List<Map<String, Object>>> datos;
    private InformesViewModel vm;

    @BeforeEach
    void preparar() {
        fachada = new FachadaFalsa();
        datos = new EnumMap<>(TipoInforme.class);
        datos.put(TipoInforme.USUARIOS_REGISTRADOS, List.of(
                fila("codigo", "20201001", "nombres", "Ana", "apellidos", "Pérez", "programa", "Sistemas", "rol", "USUARIO")));
        datos.put(TipoInforme.USUARIOS_CONECTADOS, List.of(
                fila("codigo", "20201001", "nombreCompleto", "Ana Pérez", "cantidadSesiones", 2),
                fila("codigo", "20201002", "nombreCompleto", "Luis Gómez", "cantidadSesiones", 1)));
        datos.put(TipoInforme.MENSAJES, List.of(
                fila("fecha", "2026-10-25T20:00:27.728352929", "tipo", "TEXTO", "canal", "FORO", "remitente", "20201001",
                        "hash", "a".repeat(64)),
                fila("fecha", "2026-10-24T09:00:00", "tipo", "IMAGEN", "canal", "PRIVADO", "remitente", "20201002",
                        "destinatario", "20201001", "hash", "b".repeat(64))));
        datos.put(TipoInforme.LOGS, List.of(
                fila("fecha", "2026-10-25T19:59:57.19195397", "tipo", "CONEXION", "ip", "127.0.0.1", "descripcion", "x"),
                fila("fecha", "2026-10-25T20:01:00", "tipo", "ERROR", "usuario", "20201002", "descripcion", "falló"),
                fila("fecha", "2026-10-25T20:02:00", "tipo", "CUPO_LLENO", "usuario", "20201003", "descripcion", "lleno"),
                fila("fecha", "2026-10-25T20:03:00", "tipo", "ERROR", "descripcion", "otro")));
        fachada.informes = (t, f) -> CompletableFuture.completedFuture(informe(t, f.isEmpty()
                ? datos.get(t) : datos.get(t).stream().filter(x -> x.get("tipo").equals(f.get("tipoEvento"))).toList()));
        vm = new InformesViewModel(fachada, Runnable::run, Runnable::run, RELOJ);
    }

    @Test
    void iniciarMuestraRegistradosYCalculaLasTarjetas() {
        vm.iniciar();

        assertEquals(TipoInforme.USUARIOS_REGISTRADOS, vm.tipoProperty().get());
        assertEquals(1, vm.filas().size());
        assertTrue(vm.jsonProperty().get().contains("\"tipo\" : \"USUARIOS_REGISTRADOS\""), vm.jsonProperty().get());
        assertEquals(3, vm.sesionesActivasProperty().get(), "suma de cantidadSesiones");
        assertEquals(2, vm.usuariosConectadosProperty().get());
        assertEquals(1, vm.mensajesHoyProperty().get(), "solo el del 25");
        assertEquals(2, vm.erroresProperty().get());
        assertEquals(4, fachada.llamadas.size());
    }

    @Test
    void paginaDeACincuentaEnElCliente() {
        var muchos = new ArrayList<Map<String, Object>>();
        IntStream.range(0, 120).forEach(i -> muchos.add(fila("codigo", String.valueOf(i))));
        datos.put(TipoInforme.USUARIOS_REGISTRADOS, muchos);
        vm.cargar(TipoInforme.USUARIOS_REGISTRADOS, Map.of());

        assertEquals(50, vm.filas().size());
        assertEquals("Fuente: comando INFORME → JSON del servidor · 1-50 de 120 registros", vm.textoPaginacionProperty().get());
        assertFalse(vm.hayAnteriorProperty().get());
        vm.paginaSiguiente();
        assertEquals("51", vm.filas().get(1).get("codigo"));
        vm.paginaSiguiente();
        assertEquals(20, vm.filas().size());
        assertTrue(vm.textoPaginacionProperty().get().endsWith("101-120 de 120 registros"));
        assertFalse(vm.haySiguienteProperty().get());
        vm.paginaSiguiente();
        assertEquals(2, vm.paginaProperty().get(), "no pasa de la última");
        vm.irAPagina(0);
        assertEquals("0", vm.filas().get(0).get("codigo"));

        datos.put(TipoInforme.USUARIOS_REGISTRADOS, List.of());
        vm.recargar();
        assertTrue(vm.textoPaginacionProperty().get().endsWith("0 de 0 registros"));
    }

    @Test
    void losFiltrosDeLogsSeEnvianComoParametrosYNoCambianLasTarjetas() {
        vm.iniciar();
        vm.tipoEventoProperty().set("ERROR");
        vm.usuarioProperty().set("  20201002 ");
        vm.desdeProperty().set(LocalDate.of(2026, 10, 25));
        vm.hastaProperty().set(LocalDate.of(2026, 10, 25));

        vm.filtrar();

        assertEquals(Map.of("tipoEvento", "ERROR", "usuario", "20201002", "desde", "2026-10-25T00:00:00",
                "hasta", "2026-10-25T23:59:59.999999999"), fachada.filtrosPedidos.get(fachada.filtrosPedidos.size() - 1));
        assertEquals(TipoInforme.LOGS, vm.tipoProperty().get());
        assertEquals(2, vm.filas().size());
        assertEquals(2, vm.erroresProperty().get(), "un informe filtrado no altera las tarjetas");

        vm.tipoEventoProperty().set(InformesViewModel.TODOS);
        vm.usuarioProperty().set("");
        vm.desdeProperty().set(null);
        vm.hastaProperty().set(null);
        assertEquals(Map.of(), vm.filtrosLogs());
    }

    @Test
    void unRangoDeFechasInvertidoNoLlegaAlServidor() {
        vm.desdeProperty().set(LocalDate.of(2026, 10, 26));
        vm.hastaProperty().set(LocalDate.of(2026, 10, 25));

        vm.filtrar();

        assertEquals(InformesViewModel.FECHAS_INVERTIDAS, vm.errorProperty().get());
        assertTrue(fachada.llamadas.isEmpty());
    }

    @Test
    void unaRespuestaViejaNoPisaLaMasReciente() {
        var lenta = new CompletableFuture<InformeRecibido>();
        fachada.informes = (t, f) -> t == TipoInforme.MENSAJES ? lenta
                : CompletableFuture.completedFuture(informe(t, datos.get(t)));

        vm.cargar(TipoInforme.MENSAJES, Map.of());
        assertTrue(vm.filas().isEmpty(), "al cambiar de informe la tabla se vacía de inmediato");
        assertTrue(vm.cargandoProperty().get());
        vm.cargar(TipoInforme.LOGS, Map.of());
        lenta.complete(informe(TipoInforme.MENSAJES, datos.get(TipoInforme.MENSAJES)));

        assertEquals(TipoInforme.LOGS, vm.tipoProperty().get());
        assertEquals(4, vm.filas().size());
        assertFalse(vm.cargandoProperty().get());
    }

    @Test
    void unErrorDelServidorSeMuestra() {
        fachada.informes = (t, f) -> CompletableFuture.failedFuture(
                new ExcepcionServidor(CodigoError.PETICION_INVALIDA, "Filtro inválido desde=ayer"));

        vm.cargar(TipoInforme.LOGS, Map.of("desde", "ayer"));

        assertEquals("No se pudo cargar el informe: Filtro inválido desde=ayer", vm.errorProperty().get());
        assertFalse(vm.cargandoProperty().get());
    }

    @Test
    void lasColumnasSonLasDeS3ConSuFormato() {
        vm.cargar(TipoInforme.MENSAJES, Map.of());
        var columnas = vm.columnas(TipoInforme.MENSAJES);
        Map<String, Object> foro = vm.filas().get(0);
        Map<String, Object> privado = vm.filas().get(1);

        assertEquals(List.of("Hora", "Tipo", "Canal", "Remitente", "Destino", "Hash"),
                columnas.stream().map(InformesViewModel.Columna::titulo).toList());
        assertEquals("25/10 20:00:27", columnas.get(0).valor().apply(foro));
        assertEquals("todos", columnas.get(4).valor().apply(foro));
        assertEquals("20201001", columnas.get(4).valor().apply(privado));
        assertEquals("aaaaaaaaaaaa…", columnas.get(5).valor().apply(foro));
        assertEquals(List.of("Código", "Nombre", "Cantidad de sesiones"),
                vm.columnas(TipoInforme.USUARIOS_CONECTADOS).stream().map(InformesViewModel.Columna::titulo).toList());
        assertEquals("2", vm.columnas(TipoInforme.USUARIOS_CONECTADOS).get(2).valor()
                .apply(datos.get(TipoInforme.USUARIOS_CONECTADOS).get(0)));
        assertEquals(List.of("Hora", "Tipo", "Usuario", "IP", "Descripción"),
                vm.columnas(TipoInforme.LOGS).stream().map(InformesViewModel.Columna::titulo).toList());
    }

    @Test
    void enLogsLasFilasDeCupoYErrorTienenSuColor() {
        vm.cargar(TipoInforme.LOGS, Map.of());

        assertNull(vm.estiloFila(vm.filas().get(0)));
        assertEquals("fila-error", vm.estiloFila(vm.filas().get(1)));
        assertEquals("fila-cupo", vm.estiloFila(vm.filas().get(2)));
        assertTrue(vm.tiposEvento().containsAll(List.of(InformesViewModel.TODOS, "CUPO_LLENO", "ERROR")));
    }

    private static InformeRecibido informe(TipoInforme t, List<Map<String, Object>> filas) {
        var i = new InformeDTO(t, LocalDateTime.of(2026, 10, 25, 21, 0), "20201001", filas);
        return new InformeRecibido(i, "{\n  \"tipo\" : \"" + t + "\"\n}");
    }

    private static Map<String, Object> fila(Object... claveValor) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < claveValor.length; i += 2) {
            m.put((String) claveValor[i], claveValor[i + 1]);
        }
        return m;
    }
}
