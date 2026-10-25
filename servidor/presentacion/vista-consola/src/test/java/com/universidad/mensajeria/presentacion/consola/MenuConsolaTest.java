package com.universidad.mensajeria.presentacion.consola;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuConsolaTest {

    @Test
    void elSeparadorSeAdaptaALaCodificacionDelTerminal() {
        var utf8 = new ByteArrayOutputStream();
        new MenuConsola(new BufferedReader(new StringReader("")), new PrintStream(utf8, true, StandardCharsets.UTF_8),
                new ReentrantLock()).mostrarOpciones();
        assertTrue(utf8.toString(StandardCharsets.UTF_8).contains("─".repeat(62)));

        var latin1 = new ByteArrayOutputStream();
        new MenuConsola(new BufferedReader(new StringReader("")), new PrintStream(latin1, true, StandardCharsets.ISO_8859_1),
                new ReentrantLock()).mostrarOpciones();
        String texto = latin1.toString(StandardCharsets.ISO_8859_1);
        assertTrue(texto.contains("-".repeat(62)));
        assertFalse(texto.contains("?"), "ningún carácter queda como «?»");
    }

    @Test
    void leerOpcionDistingueValidasInvalidasYFinDeEntrada() {
        var menu = menu("3\n 0 \n8\n-1\nabc\n");

        assertEquals(3, menu.leerOpcion());
        assertEquals(0, menu.leerOpcion());
        assertEquals(MenuConsola.OPCION_INVALIDA, menu.leerOpcion());
        assertEquals(MenuConsola.OPCION_INVALIDA, menu.leerOpcion());
        assertEquals(MenuConsola.OPCION_INVALIDA, menu.leerOpcion());
        assertEquals(MenuConsola.FIN_DE_ENTRADA, menu.leerOpcion());
    }

    @Test
    void sangrarRespetaLasCadenasYLosVacios() {
        String json = "{\"tipo\":\"LOGS\",\"filas\":[{\"d\":\"a, b: {c}\"}],\"vacia\":[],\"o\":{}}";

        assertEquals(String.join("\n",
                "{",
                "  \"tipo\": \"LOGS\",",
                "  \"filas\": [",
                "    {",
                "      \"d\": \"a, b: {c}\"",
                "    }",
                "  ],",
                "  \"vacia\": [],",
                "  \"o\": {}",
                "}"), MenuConsola.sangrar(json));
    }

    @Test
    void sangrarRespetaLasComillasEscapadas() {
        assertEquals("{\n  \"t\": \"dijo \\\"hola, {}\\\"\"\n}", MenuConsola.sangrar("{\"t\":\"dijo \\\"hola, {}\\\"\"}"));
    }

    private static MenuConsola menu(String entrada) {
        return new MenuConsola(new BufferedReader(new StringReader(entrada)),
                new PrintStream(new ByteArrayOutputStream()), new ReentrantLock());
    }
}
