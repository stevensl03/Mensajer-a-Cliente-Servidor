package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.ResultadoImagenDTO;
import com.universidad.mensajeria.dto.ResultadoTextoDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricaMensajesTest {

    private static final LocalDateTime RECIBIDO = LocalDateTime.of(2026, 10, 13, 10, 42, 5);

    private final FabricaMensajes fabrica = new FabricaMensajes();

    @Test
    void deTextoCopiaLaSolicitudYElResultado() {
        var s = solicitud(Canal.PRIVADO, "20201002", new ContenidoDTO(TipoMensaje.TEXTO, "Hola Luis", null, null));

        MensajeDTO dto = fabrica.deTexto(s, new ResultadoTextoDTO("abc123", 9, 2)).aDTO();

        assertEquals("m1", dto.id());
        assertEquals(TipoMensaje.TEXTO, dto.tipo());
        assertEquals(Canal.PRIVADO, dto.canal());
        assertEquals("20201001", dto.remitente());
        assertEquals("20201002", dto.destinatario());
        assertEquals(RECIBIDO, dto.fecha());
        assertEquals("abc123", dto.hash());
        assertEquals("Hola Luis", dto.texto());
        assertEquals(9, dto.numCaracteres());
        assertEquals(2, dto.numPalabras());
        assertNull(dto.nombreArchivo());
    }

    @Test
    void deImagenGuardaLasSeisVersionesEnOrden() {
        var s = solicitud(Canal.FORO, null, new ContenidoDTO(TipoMensaje.IMAGEN, null, "foto.png", "AAAA"));
        List<ImagenProcesadaDTO> versiones = Arrays.stream(TipoFiltro.values())
                .map(f -> new ImagenProcesadaDTO(f, new byte[]{(byte) f.ordinal()}, "h" + f.ordinal()))
                .toList();

        Mensaje m = fabrica.deImagen(s, new ResultadoImagenDTO("hashOriginal", versiones));

        MensajeImagen imagen = (MensajeImagen) m;
        assertEquals(TipoMensaje.IMAGEN, m.tipo());
        assertEquals(List.of(TipoFiltro.values()), imagen.versiones().stream().map(ImagenProcesadaDTO::etapa).toList());
        MensajeDTO dto = m.aDTO();
        assertEquals("foto.png", dto.nombreArchivo());
        assertEquals("hashOriginal", dto.hash());
        assertNull(dto.destinatario());
        assertTrue(dto.imagenes().isEmpty(), "las rutas las asigna AlmacenarInformacion al guardar");
    }

    @Test
    void rechazaContenidoDeOtroTipo() {
        var texto = solicitud(Canal.FORO, null, new ContenidoDTO(TipoMensaje.TEXTO, "Hola", null, null));
        var imagen = solicitud(Canal.FORO, null, new ContenidoDTO(TipoMensaje.IMAGEN, null, "foto.png", "AAAA"));

        assertThrows(IllegalArgumentException.class,
                () -> fabrica.deImagen(texto, new ResultadoImagenDTO("h", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> fabrica.deTexto(imagen, new ResultadoTextoDTO("h", 0, 0)));
    }

    @Test
    void claveConversacionNoDependeDelOrden() {
        assertEquals("a|b", Mensaje.claveConversacion("b", "a"));
        assertEquals("a|b", Mensaje.claveConversacion("a", "b"));
        assertEquals("20201001|20201002", Mensaje.claveConversacion("20201002", "20201001"));
    }

    @Test
    void foroNoLlevaDestinatarioYPrivadoLoExige() {
        var foroConDestino = solicitud(Canal.FORO, "20201002", new ContenidoDTO(TipoMensaje.TEXTO, "x", null, null));
        var privadoSinDestino = solicitud(Canal.PRIVADO, null, new ContenidoDTO(TipoMensaje.TEXTO, "x", null, null));
        var r = new ResultadoTextoDTO("h", 1, 1);

        assertThrows(IllegalArgumentException.class, () -> fabrica.deTexto(foroConDestino, r));
        assertThrows(IllegalArgumentException.class, () -> fabrica.deTexto(privadoSinDestino, r));
    }

    private static SolicitudMensajeDTO solicitud(Canal canal, String destino, ContenidoDTO contenido) {
        return new SolicitudMensajeDTO("m1", "p1", "s1", "20201001", canal, destino, contenido, RECIBIDO);
    }
}
