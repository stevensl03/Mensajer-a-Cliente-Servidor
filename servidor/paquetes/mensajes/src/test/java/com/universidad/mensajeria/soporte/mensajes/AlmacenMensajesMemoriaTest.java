package com.universidad.mensajeria.soporte.mensajes;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenMensajesMemoriaTest {

    private static final String ANA = "20201001";
    private static final String LUIS = "20201002";
    private static final String MARIA = "20201003";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 13, 10, 0);

    private final AlmacenFalso almacen = new AlmacenFalso();
    private final AlmacenMensajesMemoria mensajes = new AlmacenMensajesMemoria(almacen);

    @Test
    void agregarPersisteYDevuelveLoGuardado() {
        MensajeDTO dto = mensajes.agregar(texto("m1", Canal.FORO, ANA, null, 0));

        assertEquals(List.of("m1"), almacen.guardados.stream().map(MensajeDTO::id).toList());
        assertEquals(dto, mensajes.buscar("m1").orElseThrow());
        assertTrue(mensajes.buscar("otro").isEmpty());
    }

    @Test
    void agregarImagenEntregaLasVersionesYGuardaLasRutas() {
        List<ImagenProcesadaDTO> versiones = Arrays.stream(TipoFiltro.values())
                .map(f -> new ImagenProcesadaDTO(f, new byte[]{1, 2}, "h" + f.ordinal()))
                .toList();
        var imagen = new MensajeImagen("m1", Canal.FORO, ANA, null, T0, "h0", "foto.png", versiones);

        MensajeDTO dto = mensajes.agregar(imagen);

        assertEquals(6, almacen.ultimasVersiones.size());
        assertEquals(6, dto.imagenes().size());
        assertEquals(dto, mensajes.foro().get(0), "se indexa el DTO con las rutas");
    }

    @Test
    void siFallaElGuardadoNoSeIndexa() {
        almacen.fallarAlGuardar = true;

        assertThrows(IllegalStateException.class, () -> mensajes.agregar(texto("m1", Canal.FORO, ANA, null, 0)));

        assertTrue(mensajes.buscar("m1").isEmpty());
        assertTrue(mensajes.foro().isEmpty());
        assertTrue(mensajes.todos().isEmpty());
    }

    @Test
    void foroSoloTraeMensajesDelForoOrdenadosPorFecha() {
        mensajes.agregar(texto("m2", Canal.FORO, LUIS, null, 2));
        mensajes.agregar(texto("p1", Canal.PRIVADO, ANA, LUIS, 1));
        mensajes.agregar(texto("m1", Canal.FORO, ANA, null, 1));

        assertEquals(List.of("m1", "m2"), ids(mensajes.foro()));
    }

    @Test
    void conversacionEsLaMismaEnAmbosSentidos() {
        mensajes.agregar(texto("p1", Canal.PRIVADO, ANA, LUIS, 1));
        mensajes.agregar(texto("p2", Canal.PRIVADO, LUIS, ANA, 2));
        mensajes.agregar(texto("p3", Canal.PRIVADO, ANA, MARIA, 3));
        mensajes.agregar(texto("f1", Canal.FORO, ANA, null, 4));

        assertEquals(List.of("p1", "p2"), ids(mensajes.conversacion(ANA, LUIS)));
        assertEquals(List.of("p1", "p2"), ids(mensajes.conversacion(LUIS, ANA)));
        assertEquals(List.of("p3"), ids(mensajes.conversacion(MARIA, ANA)));
        assertTrue(mensajes.conversacion(LUIS, MARIA).isEmpty());
    }

    @Test
    void todosConservaElOrdenDeLlegadaYEsUnaCopia() {
        mensajes.agregar(texto("m2", Canal.FORO, ANA, null, 2));
        mensajes.agregar(texto("p1", Canal.PRIVADO, ANA, LUIS, 1));
        List<MensajeDTO> copia = mensajes.todos();

        mensajes.agregar(texto("m3", Canal.FORO, ANA, null, 3));

        assertEquals(List.of("m2", "p1"), ids(copia));
        assertThrows(UnsupportedOperationException.class, () -> copia.add(copia.get(0)));
    }

    @Test
    void cargarHistorialIndexaEnOrdenSinDuplicarNiVolverAGuardar() {
        almacen.historial.add(texto("p1", Canal.PRIVADO, LUIS, ANA, 2).aDTO());
        almacen.historial.add(texto("m1", Canal.FORO, ANA, null, 1).aDTO());

        mensajes.cargarHistorial();
        mensajes.cargarHistorial();

        assertEquals(List.of("m1", "p1"), ids(mensajes.todos()));
        assertEquals(List.of("m1"), ids(mensajes.foro()));
        assertEquals(List.of("p1"), ids(mensajes.conversacion(ANA, LUIS)));
        assertTrue(almacen.guardados.isEmpty());
    }

    @Test
    void imagenPasaPorAlmacenarInformacion() {
        almacen.imagenes.put("m1:SEPIA", new byte[]{7, 8});

        assertArrayEquals(new byte[]{7, 8}, mensajes.imagen("m1", TipoFiltro.SEPIA).orElseThrow());
        assertTrue(mensajes.imagen("m1", TipoFiltro.BRILLO).isEmpty());
    }

    private static MensajeTexto texto(String id, Canal canal, String de, String para, int minuto) {
        return new MensajeTexto(id, canal, de, para, T0.plusMinutes(minuto), "hash-" + id, "Hola " + id, 6, 2);
    }

    private static List<String> ids(List<MensajeDTO> l) {
        return l.stream().map(MensajeDTO::id).toList();
    }

    /** IAlmacenarInformacion sin BD: asigna rutas falsas a las imágenes y graba lo que recibe. */
    private static final class AlmacenFalso implements IAlmacenarInformacion {
        final List<MensajeDTO> guardados = new ArrayList<>();
        final List<MensajeDTO> historial = new ArrayList<>();
        final Map<String, byte[]> imagenes = new HashMap<>();
        List<ImagenProcesadaDTO> ultimasVersiones = List.of();
        boolean fallarAlGuardar;

        @Override
        public MensajeDTO guardarMensaje(MensajeDTO m, List<ImagenProcesadaDTO> imgs) {
            if (fallarAlGuardar) {
                throw new IllegalStateException("BD caída");
            }
            ultimasVersiones = imgs;
            MensajeDTO guardado = m;
            if (m.tipo() == TipoMensaje.IMAGEN) {
                List<ImagenVersionDTO> rutas = imgs.stream()
                        .map(i -> new ImagenVersionDTO(i.etapa().ordinal(), i.etapa(), "f" + i.etapa().ordinal(),
                                "/almacen/" + m.id() + "_" + i.etapa(), i.datos().length, i.hash()))
                        .toList();
                guardado = new MensajeDTO(m.id(), m.tipo(), m.canal(), m.remitente(), m.destinatario(), m.fecha(),
                        m.hash(), m.texto(), m.numCaracteres(), m.numPalabras(), m.nombreArchivo(), rutas);
            }
            guardados.add(guardado);
            return guardado;
        }

        @Override
        public Optional<byte[]> leerImagen(String idMensaje, TipoFiltro etapa) {
            return Optional.ofNullable(imagenes.get(idMensaje + ":" + etapa));
        }

        @Override
        public List<MensajeDTO> historialMensajes() {
            return List.copyOf(historial);
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
        public void registrarConexion(SesionDTO s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registrarDesconexion(String idSesion, LocalDateTime fin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void guardarLog(EventoLogDTO e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EventoLogDTO> historialLogs() {
            throw new UnsupportedOperationException();
        }
    }
}
