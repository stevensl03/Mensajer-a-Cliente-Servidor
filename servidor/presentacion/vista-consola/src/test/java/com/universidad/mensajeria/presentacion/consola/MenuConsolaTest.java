package com.universidad.mensajeria.presentacion.consola;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuConsolaTest {

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
