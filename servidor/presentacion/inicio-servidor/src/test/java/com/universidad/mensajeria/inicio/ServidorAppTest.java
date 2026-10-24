package com.universidad.mensajeria.inicio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServidorAppTest {

    @Test
    void sinArgumentosUsaLaVistaConfigurada() {
        assertEquals(TipoVista.CONSOLA, ServidorApp.tipoVista(new String[0], "CONSOLA"));
        assertEquals(TipoVista.ESCRITORIO, ServidorApp.tipoVista(new String[0], "ESCRITORIO"));
    }

    @Test
    void elArgumentoVistaManda() {
        assertEquals(TipoVista.ESCRITORIO, ServidorApp.tipoVista(new String[]{"--vista=ESCRITORIO"}, "CONSOLA"));
        assertEquals(TipoVista.ESCRITORIO, ServidorApp.tipoVista(new String[]{"--vista", "escritorio"}, "CONSOLA"));
        assertEquals(TipoVista.CONSOLA, ServidorApp.tipoVista(new String[]{"--otro", "--vista=consola"}, "ESCRITORIO"));
    }

    @Test
    void unaVistaDesconocidaFallaConUnMensajeClaro() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> ServidorApp.tipoVista(new String[]{"--vista=WEB"}, "CONSOLA"));
        assertEquals("Vista desconocida: WEB (use CONSOLA o ESCRITORIO)", e.getMessage());
    }
}
