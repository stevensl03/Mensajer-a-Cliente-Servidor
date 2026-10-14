package com.universidad.mensajeria.utilerias;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato JSON de los DTO (§7): ida y vuelta de cada record, fechas ISO sin
 * zona, nulos omitidos y enums con los nombres y el orden del catálogo.
 */
class DtoJsonTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 13, 10, 42, 5, 118_000_000);
    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final ImagenVersionDTO VERSION =
            new ImagenVersionDTO(1, TipoFiltro.ESCALA_GRISES, "m1_1_ESCALA_GRISES.png",
                    "./almacen/filtradas/m1_1_ESCALA_GRISES.png", 1234, "ab".repeat(32));

    private final ISerializador json = new SerializadorJSON();

    private <T> void idaYVuelta(T dto, Class<T> tipo) {
        assertEquals(dto, json.desdeJson(json.aJson(dto), tipo), tipo.getSimpleName());
    }

    @Test
    void cadaRecordSobreviveIdaYVuelta() {
        JsonNode datos = json.aArbol(Map.of("idMensaje", "m1", "estado", "ENCOLADO"));
        var contenido = new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null);

        idaYVuelta(ANA, UsuarioDTO.class);
        idaYVuelta(new UsuarioRegistroDTO("20201001", "Ana", "Pérez", "f".repeat(64), "Sistemas", Rol.ADMIN),
                UsuarioRegistroDTO.class);
        idaYVuelta(new EstadoUsuarioDTO(ANA, true, 2), EstadoUsuarioDTO.class);
        idaYVuelta(new SesionDTO("s1", "20201001", "Ana Pérez", "10.0.0.5", FECHA), SesionDTO.class);
        idaYVuelta(new ConexionUsuarioDTO("20201001", "Ana Pérez", 3), ConexionUsuarioDTO.class);
        idaYVuelta(contenido, ContenidoDTO.class);
        idaYVuelta(new PeticionDTO("p1", TipoComando.ENVIAR_PRIVADO, Map.of("destino", "20201002"), contenido),
                PeticionDTO.class);
        idaYVuelta(new RespuestaDTO("p1", true, CodigoError.OK, "Encolado", datos), RespuestaDTO.class);
        idaYVuelta(new EventoPushDTO(TipoEventoPush.MENSAJE_NUEVO, FECHA, datos), EventoPushDTO.class);
        idaYVuelta(new SolicitudMensajeDTO("m1", "p1", "s1", "20201001", Canal.PRIVADO, "20201002", contenido, FECHA),
                SolicitudMensajeDTO.class);
        idaYVuelta(new MensajeDTO("m1", TipoMensaje.IMAGEN, Canal.FORO, "20201001", null, FECHA, "h",
                null, 0, 0, "foto.png", List.of(VERSION)), MensajeDTO.class);
        idaYVuelta(VERSION, ImagenVersionDTO.class);
        idaYVuelta(new ResultadoTextoDTO("h", 4, 1), ResultadoTextoDTO.class);
        idaYVuelta(new EventoLogDTO("e1", TipoEvento.LOGIN_OK, "LOGIN_OK", "20201001", "10.0.0.5", FECHA),
                EventoLogDTO.class);
    }

    @Test
    void lasImagenesViajanEnBase64() {
        byte[] bytes = {1, 2, 3, (byte) 255};
        var original = new ResultadoImagenDTO("h", List.of(new ImagenProcesadaDTO(TipoFiltro.ORIGINAL, bytes, "h")));

        var leido = json.desdeJson(json.aJson(original), ResultadoImagenDTO.class);

        assertEquals(TipoFiltro.ORIGINAL, leido.imagenes().get(0).etapa());
        assertArrayEquals(bytes, leido.imagenes().get(0).datos());
    }

    @Test
    void informeConservaTipoYFilas() {
        var informe = new InformeDTO(TipoInforme.USUARIOS_REGISTRADOS, FECHA, "ADMIN01", List.of(ANA));

        var leido = json.desdeJson(json.aJson(informe), InformeDTO.class);

        assertEquals(TipoInforme.USUARIOS_REGISTRADOS, leido.tipo());
        assertEquals(FECHA, leido.generado());
        assertEquals(ANA, json.convertir(json.aArbol(leido.filas().get(0)), UsuarioDTO.class));
    }

    @Test
    void fechasIsoSinZonaYNulosOmitidos() {
        String texto = json.aJson(new EventoPushDTO(TipoEventoPush.SESION_CERRADA, FECHA, null));

        assertEquals("{\"evento\":\"SESION_CERRADA\",\"fecha\":\"2026-10-13T10:42:05.118\"}", texto);
        assertFalse(json.aJson(new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null)).contains("null"));
    }

    @Test
    void respuestaYEventoSegunLosEjemplosDelProtocolo() {
        JsonNode datos = json.aArbol(Map.of("idMensaje", "m1"));
        String respuesta = json.aJson(new RespuestaDTO("7f1c", true, CodigoError.OK, "Encolado", datos));
        String evento = json.aJson(new EventoPushDTO(TipoEventoPush.MENSAJE_NUEVO, FECHA, datos));

        assertTrue(respuesta.startsWith("{\"idPeticion\":\"7f1c\",\"exito\":true,\"codigo\":\"OK\""), respuesta);
        assertTrue(evento.startsWith("{\"evento\":\"MENSAJE_NUEVO\""), evento);
        assertFalse(evento.contains("idPeticion"));
    }

    @Test
    void validacionEnLosConstructores() {
        assertThrows(IllegalArgumentException.class,
                () -> new RespuestaDTO("p1", true, CodigoError.COLA_LLENA, "x", null));
        assertThrows(NullPointerException.class, () -> new RespuestaDTO("p1", false, null, "x", null));
        assertThrows(IllegalArgumentException.class, () -> new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.FORO,
                "a", "b", FECHA, "h", "t", 1, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.PRIVADO,
                "a", null, FECHA, "h", "t", 1, 1, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ImagenVersionDTO(6, TipoFiltro.ORIGINAL, "n", "r", 1, "h"));
        assertThrows(NullPointerException.class, () -> new UsuarioDTO("1", null, "x", "p", Rol.USUARIO));
        assertThrows(IllegalArgumentException.class, () -> new EstadoUsuarioDTO(ANA, false, -1));
    }

    @Test
    void lasListasSeCopian() {
        var versiones = new java.util.ArrayList<>(List.of(VERSION));
        var m = new MensajeDTO("m1", TipoMensaje.IMAGEN, Canal.FORO, "a", null, FECHA, "h",
                null, 0, 0, "f.png", versiones);
        versiones.clear();

        assertEquals(1, m.imagenes().size());
        assertThrows(UnsupportedOperationException.class, () -> m.imagenes().clear());
    }

    @Test
    void enumsConLosNombresYElOrdenDelCatalogo() {
        assertOrden(Rol.values(), "USUARIO", "ADMIN");
        assertOrden(Canal.values(), "FORO", "PRIVADO");
        assertOrden(TipoMensaje.values(), "TEXTO", "IMAGEN");
        assertOrden(TipoComando.values(), "LOGIN", "LOGOUT", "LISTAR_USUARIOS", "ENVIAR_FORO", "ENVIAR_PRIVADO",
                "HISTORIAL", "INFORME", "OBTENER_IMAGEN");
        assertOrden(TipoEventoPush.values(), "INFO_SERVIDOR", "MENSAJE_NUEVO", "USUARIO_CONECTADO",
                "USUARIO_DESCONECTADO", "SESION_CERRADA", "CUPO_LLENO");
        assertOrden(CodigoError.values(), "OK", "PETICION_INVALIDA", "NO_AUTENTICADO", "CREDENCIALES_INVALIDAS",
                "CUPO_LLENO", "DESTINO_INEXISTENTE", "CONTENIDO_INVALIDO", "COLA_LLENA", "NO_ENCONTRADO",
                "ERROR_INTERNO");
        assertOrden(TipoEvento.values(), "CONEXION", "DESCONEXION", "LOGIN_OK", "LOGIN_FALLIDO", "CUPO_LLENO",
                "MENSAJE_FORO", "MENSAJE_PRIVADO", "ERROR");
        assertOrden(TipoFiltro.values(), "ORIGINAL", "ESCALA_GRISES", "SEPIA", "ROTAR_180", "BRILLO",
                "REDUCCION_TAMANO");
        assertOrden(TipoInforme.values(), "USUARIOS_REGISTRADOS", "USUARIOS_CONECTADOS", "MENSAJES", "LOGS");
    }

    private static void assertOrden(Enum<?>[] valores, String... esperados) {
        assertEquals(List.of(esperados), Arrays.stream(valores).map(Enum::name).toList());
    }
}
