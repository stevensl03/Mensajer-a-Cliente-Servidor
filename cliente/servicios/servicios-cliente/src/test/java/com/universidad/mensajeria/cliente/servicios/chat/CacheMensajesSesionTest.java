package com.universidad.mensajeria.cliente.servicios.chat;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheMensajesSesionTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 25, 20, 0);

    @Test
    void soloAgregaAConversacionesCargadasSinDuplicarYEnOrden() {
        var cache = new CacheMensajesSesion(5);
        cache.agregar(foro("suelto", 0));
        assertFalse(cache.contiene("FORO"), "un mensaje suelto no carga la conversación (D-18)");

        cache.cargar("FORO", List.of(foro("b", 2), foro("a", 1), foro("a", 1)));
        cache.agregar(foro("c", 0));
        cache.agregar(foro("b", 2));

        assertEquals(List.of("c", "a", "b"), cache.get("FORO").stream().map(MensajeDTO::id).toList());
    }

    @Test
    void desalojaLaConversacionMenosUsada() {
        var cache = new CacheMensajesSesion(2);
        cache.cargar("x|a", List.of());
        cache.cargar("x|b", List.of());
        cache.get("x|a");

        cache.cargar("x|c", List.of());

        assertTrue(cache.contiene("x|a"));
        assertFalse(cache.contiene("x|b"));
        assertTrue(cache.contiene("x|c"));
    }

    @Test
    void getDevuelveUnaCopiaYLimpiarVacia() {
        var cache = new CacheMensajesSesion(2);
        cache.cargar("FORO", List.of(foro("a", 0)));

        assertThrows(UnsupportedOperationException.class, () -> cache.get("FORO").add(foro("z", 9)));
        assertTrue(cache.get("otra").isEmpty());
        cache.limpiar();
        assertFalse(cache.contiene("FORO"));
        assertThrows(IllegalArgumentException.class, () -> new CacheMensajesSesion(0));
    }

    private static MensajeDTO foro(String id, int segundos) {
        return new MensajeDTO(id, TipoMensaje.TEXTO, Canal.FORO, "20201002", null, T0.plusSeconds(segundos),
                "h", id, 1, 1, null, List.of());
    }
}
