package com.universidad.mensajeria.cliente.utilerias;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializadorJSONTest {

    private final ISerializador json = new SerializadorJSON();

    private static final UsuarioDTO MARIA =
            new UsuarioDTO("20201003", "María", "Rodríguez", "Ingeniería Electrónica", Rol.USUARIO);

    @Test
    void idaYVueltaDeUnUsuario() {
        assertEquals(MARIA, json.desdeJson(json.aJson(MARIA), UsuarioDTO.class));
    }

    @Test
    void idaYVueltaDeUnMensajeConImagenes() {
        var versiones = List.of(
                new ImagenVersionDTO(0, TipoFiltro.ORIGINAL, "foto.png", "a/foto.png", 120, "h0"),
                new ImagenVersionDTO(1, TipoFiltro.ESCALA_GRISES, "foto_gris.png", "a/foto_gris.png", 100, "h1"));
        var m = new MensajeDTO("m1", TipoMensaje.IMAGEN, Canal.PRIVADO, "20201003", "20201001",
                LocalDateTime.of(2026, 10, 25, 20, 0, 27, 728_352_929), "hash", null, 0, 0, "foto.png", versiones);

        assertEquals(m, json.desdeJson(json.aJson(m), MensajeDTO.class));
    }

    @Test
    void laPeticionOmiteLosNulos() {
        var p = new PeticionDTO("p1", TipoComando.ENVIAR_FORO, Map.of(),
                new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null));

        String texto = json.aJson(p);

        assertFalse(texto.contains("nombreArchivo"), texto);
        assertFalse(texto.contains("datosBase64"), texto);
        assertEquals(p, json.desdeJson(texto, PeticionDTO.class));
    }

    @Test
    void leeFechasConTresYNueveDecimales() {
        String tres = "{\"evento\":\"SESION_CERRADA\",\"fecha\":\"2026-10-13T10:42:05.118\",\"datos\":{\"motivo\":\"X\"}}";
        String nueve = "{\"evento\":\"SESION_CERRADA\",\"fecha\":\"2026-10-25T20:01:01.509673883\",\"datos\":{\"motivo\":\"X\"}}";

        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 42, 5, 118_000_000),
                json.desdeJson(tres, EventoPushDTO.class).fecha());
        assertEquals(LocalDateTime.of(2026, 10, 25, 20, 1, 1, 509_673_883),
                json.desdeJson(nueve, EventoPushDTO.class).fecha());
    }

    @Test
    void leeUnPushRealYConvierteSusDatos() {
        // Extracto real de MENSAJE_NUEVO (warning.md §12), con un campo desconocido añadido.
        String push = """
                {"evento":"MENSAJE_NUEVO","fecha":"2026-10-25T20:00:27.728352929","extra":1,
                 "datos":{"mensaje":{"id":"m1","tipo":"TEXTO","canal":"FORO","remitente":"20201003",
                   "fecha":"2026-10-25T20:00:27.720395692","hash":"abc","texto":"Hola foro",
                   "numCaracteres":9,"numPalabras":2,"imagenes":[]}}}""";

        EventoPushDTO e = json.desdeJson(push, EventoPushDTO.class);
        MensajeDTO m = json.convertir(e.datos().get("mensaje"), new TypeReference<MensajeDTO>() { });

        assertEquals(TipoEventoPush.MENSAJE_NUEVO, e.evento());
        assertEquals("Hola foro", m.texto());
        assertNull(m.destinatario());
        assertTrue(m.imagenes().isEmpty());
    }

    @Test
    void convierteListasGenericasDesdeLaRespuesta() {
        String respuesta = """
                {"idPeticion":"p1","exito":true,"codigo":"OK","mensaje":"OK","datos":{"usuarios":[
                 {"usuario":{"codigo":"ADMIN01","nombres":"Admin","apellidos":"Sistema","programa":"TI","rol":"ADMIN"},
                  "conectado":true,"sesiones":0},
                 {"usuario":{"codigo":"20201001","nombres":"Ana","apellidos":"Pérez","programa":"Sistemas","rol":"USUARIO"},
                  "conectado":false,"sesiones":0}]}}""";

        RespuestaDTO r = json.desdeJson(respuesta, RespuestaDTO.class);
        List<EstadoUsuarioDTO> usuarios =
                json.convertir(r.datos().get("usuarios"), new TypeReference<List<EstadoUsuarioDTO>>() { });

        assertEquals(CodigoError.OK, r.codigo());
        assertEquals(2, usuarios.size());
        assertEquals(Rol.ADMIN, usuarios.get(0).usuario().rol());
        assertEquals("Ana", usuarios.get(1).usuario().nombres());
    }

    @Test
    void leeUnaRespuestaDeErrorConIdPeticionNulo() {
        String respuesta = "{\"idPeticion\":null,\"exito\":false,\"codigo\":\"PETICION_INVALIDA\",\"mensaje\":\"JSON mal formado\"}";

        RespuestaDTO r = json.desdeJson(respuesta, RespuestaDTO.class);

        assertNull(r.idPeticion());
        assertNull(r.datos());
        assertEquals(CodigoError.PETICION_INVALIDA, r.codigo());
    }

    @Test
    void elJsonInvalidoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> json.desdeJson("{no es json", RespuestaDTO.class));
        JsonNode sinCodigo = json.desdeJson("{\"nombres\":\"Ana\"}", JsonNode.class);
        assertThrows(IllegalArgumentException.class,
                () -> json.convertir(sinCodigo, new TypeReference<UsuarioDTO>() { }));
    }
}
