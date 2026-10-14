package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorPeticionesTest {

    private static final int MAX_TEXTO = 10;
    private static final int MAX_IMAGEN = 2048;

    private final ValidadorPeticiones validador = new ValidadorPeticiones(MAX_TEXTO, MAX_IMAGEN);

    // --- Parámetros obligatorios por comando ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "LOGIN|codigo=1,clave=x",
            "LOGOUT|",
            "LISTAR_USUARIOS|",
            "HISTORIAL|conversacion=FORO",
            "HISTORIAL|conversacion=20201002,limite=50",
            "OBTENER_IMAGEN|idMensaje=m1,etapa=SEPIA",
            "INFORME|tipo=MENSAJES",
            "INFORME|tipo=LOGS,tipoEvento=LOGIN_OK,usuario=20201001,desde=2026-10-01T00:00,hasta=2026-10-13T23:59:59.999"
    })
    void peticionesValidas(TipoComando comando, String parametros) {
        assertDoesNotThrow(() -> validador.validar(peticion(comando, parametros, null)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "LOGIN|codigo=1",
            "LOGIN|clave=x",
            "LOGIN|codigo=  ,clave=x",
            "ENVIAR_PRIVADO|",
            "HISTORIAL|",
            "HISTORIAL|conversacion=FORO,limite=0",
            "HISTORIAL|conversacion=FORO,limite=muchos",
            "OBTENER_IMAGEN|idMensaje=m1",
            "OBTENER_IMAGEN|idMensaje=m1,etapa=PIXELADO",
            "INFORME|",
            "INFORME|tipo=VENTAS",
            "INFORME|tipo=LOGS,tipoEvento=OTRO",
            "INFORME|tipo=LOGS,desde=ayer",
            "INFORME|tipo=LOGS,hasta=2026-13-01T00:00"
    })
    void parametrosFaltantesOInvalidos(TipoComando comando, String parametros) {
        var e = assertThrows(PeticionInvalidaException.class,
                () -> validador.validar(peticion(comando, parametros, texto("hola"))));
        assertEquals(CodigoError.PETICION_INVALIDA, e.getCodigo());
        assertEquals("p1", e.getIdPeticion());
    }

    // --- Contenido de texto ---

    @Test
    void textoValidoEnForoYPrivado() {
        assertDoesNotThrow(() -> validador.validar(peticion(TipoComando.ENVIAR_FORO, "", texto("Hola"))));
        assertDoesNotThrow(() -> validador.validar(peticion(TipoComando.ENVIAR_PRIVADO, "destino=2", texto("ñandú 😀"))));
        assertDoesNotThrow(() -> validador.validar(peticion(TipoComando.ENVIAR_FORO, "", texto("x".repeat(MAX_TEXTO)))));
    }

    @Test
    void contenidoDeTextoInvalido() {
        contenidoInvalido(null);
        contenidoInvalido(texto(""));
        contenidoInvalido(texto("  \n "));
        contenidoInvalido(texto(null));
        contenidoInvalido(texto("x".repeat(MAX_TEXTO + 1)));
        contenidoInvalido(new ContenidoDTO(TipoMensaje.TEXTO, "hola", "a.png", null));
        contenidoInvalido(new ContenidoDTO(TipoMensaje.TEXTO, "hola", null, "AAAA"));
    }

    @Test
    void elLimiteDeTextoCuentaCaracteresNoBytes() {
        assertDoesNotThrow(() -> validador.validar(peticion(TipoComando.ENVIAR_FORO, "", texto("😀".repeat(MAX_TEXTO)))));
    }

    // --- Contenido de imagen ---

    @ParameterizedTest
    @ValueSource(strings = {"foto.png", "foto.PNG", "f.jpg", "f.jpeg", "f.gif", "f.bmp", "mi foto (1).png"})
    void imagenValida(String nombre) throws IOException {
        assertDoesNotThrow(() -> validador.validar(peticion(TipoComando.ENVIAR_FORO, "", imagen(nombre, png()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foto.exe", "foto", "foto.png.txt", "../foto.png", "a/b.png", "a\\b.png", " "})
    void nombreDeArchivoInvalido(String nombre) throws IOException {
        contenidoInvalido(imagen(nombre, png()));
    }

    @Test
    void datosDeImagenInvalidos() throws IOException {
        contenidoInvalido(new ContenidoDTO(TipoMensaje.IMAGEN, null, "f.png", null));
        contenidoInvalido(new ContenidoDTO(TipoMensaje.IMAGEN, null, "f.png", ""));
        contenidoInvalido(new ContenidoDTO(TipoMensaje.IMAGEN, null, "f.png", "%%% no es base64 %%%"));
        contenidoInvalido(imagen("f.png", new byte[]{1, 2, 3, 4, 5}));
        contenidoInvalido(imagen("f.png", new byte[MAX_IMAGEN + 1]));
    }

    @Test
    void elLimiteDeImagenEsSobreLosBytesDecodificados() throws IOException {
        byte[] png = png();
        var justo = new ValidadorPeticiones(MAX_TEXTO, png.length);
        var corto = new ValidadorPeticiones(MAX_TEXTO, png.length - 1);

        assertDoesNotThrow(() -> justo.validar(peticion(TipoComando.ENVIAR_FORO, "", imagen("f.png", png))));
        var e = assertThrows(PeticionInvalidaException.class,
                () -> corto.validar(peticion(TipoComando.ENVIAR_FORO, "", imagen("f.png", png))));
        assertEquals(CodigoError.CONTENIDO_INVALIDO, e.getCodigo());
    }

    @Test
    void limitesInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new ValidadorPeticiones(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ValidadorPeticiones(10, 0));
    }

    private void contenidoInvalido(ContenidoDTO c) {
        for (TipoComando comando : new TipoComando[]{TipoComando.ENVIAR_FORO, TipoComando.ENVIAR_PRIVADO}) {
            var e = assertThrows(PeticionInvalidaException.class,
                    () -> validador.validar(peticion(comando, "destino=2", c)), String.valueOf(c));
            assertEquals(CodigoError.CONTENIDO_INVALIDO, e.getCodigo(), String.valueOf(c));
            assertEquals("p1", e.getIdPeticion());
        }
    }

    private static PeticionDTO peticion(TipoComando comando, String parametros, ContenidoDTO contenido) {
        Map<String, String> mapa = new HashMap<>();
        if (parametros != null && !parametros.isBlank()) {
            for (String par : parametros.split(",")) {
                String[] kv = par.split("=", 2);
                mapa.put(kv[0], kv[1]);
            }
        }
        return new PeticionDTO("p1", comando, mapa, contenido);
    }

    private static ContenidoDTO texto(String t) {
        return new ContenidoDTO(TipoMensaje.TEXTO, t, null, null);
    }

    private static ContenidoDTO imagen(String nombre, byte[] datos) {
        return new ContenidoDTO(TipoMensaje.IMAGEN, null, nombre, Base64.getEncoder().encodeToString(datos));
    }

    private static byte[] png() throws IOException {
        return UtilImagen.aBytes(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png");
    }
}
