package com.universidad.mensajeria.componentes.gestor;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class GestorColasDeMensajesTest {

    private GestorColasDeMensajes gestor;

    @AfterEach
    void detener() {
        if (gestor != null) {
            gestor.detener();
        }
    }

    @Test
    void encolarDevuelveFalseConLaColaLlena() {
        gestor = new GestorColasDeMensajes(2);

        assertTrue(gestor.encolar(solicitud("1")));
        assertTrue(gestor.encolar(solicitud("2")));
        assertFalse(gestor.encolar(solicitud("3")));
        assertEquals(2, gestor.pendientes());
    }

    @Test
    void encolarNoBloqueaAunqueLosConsumidoresEstenOcupados() throws InterruptedException {
        gestor = new GestorColasDeMensajes(1);
        var dentro = new CountDownLatch(1);
        var soltar = new CountDownLatch(1);
        gestor.iniciar(1, s -> {
            dentro.countDown();
            esperar(soltar);
        });
        gestor.encolar(solicitud("ocupa-al-consumidor"));
        assertTrue(dentro.await(5, TimeUnit.SECONDS));

        assertTrue(gestor.encolar(solicitud("llena-la-cola")));
        long inicio = System.nanoTime();
        assertFalse(gestor.encolar(solicitud("rechazada")));
        assertTrue(System.nanoTime() - inicio < TimeUnit.MILLISECONDS.toNanos(100), "offer no espera");
        soltar.countDown();
    }

    @Test
    void nConsumidoresEnHilosDePlataformaProcesanEnParalelo() throws InterruptedException {
        gestor = new GestorColasDeMensajes(100);
        int hilos = 3;
        var todosDentro = new CountDownLatch(hilos);
        var soltar = new CountDownLatch(1);
        Set<Thread> usados = ConcurrentHashMap.newKeySet();
        gestor.iniciar(hilos, s -> {
            usados.add(Thread.currentThread());
            todosDentro.countDown();
            esperar(soltar);
        });

        for (int i = 0; i < hilos; i++) {
            gestor.encolar(solicitud("m" + i));
        }

        assertTrue(todosDentro.await(5, TimeUnit.SECONDS), "los 3 mensajes se procesan a la vez");
        assertEquals(hilos, usados.size());
        usados.forEach(t -> {
            assertFalse(t.isVirtual(), t.getName());
            assertTrue(t.getName().startsWith("consumidor-"), t.getName());
        });
        soltar.countDown();
    }

    @Test
    void procesaTodoYLoQueLlegoAntesDeIniciar() throws InterruptedException {
        gestor = new GestorColasDeMensajes(100);
        List<String> procesados = new CopyOnWriteArrayList<>();
        var listos = new CountDownLatch(10);
        gestor.encolar(solicitud("antes"));

        gestor.iniciar(2, s -> {
            procesados.add(s.idMensaje());
            listos.countDown();
        });
        for (int i = 0; i < 9; i++) {
            gestor.encolar(solicitud("m" + i));
        }

        assertTrue(listos.await(5, TimeUnit.SECONDS));
        assertTrue(procesados.contains("antes"));
        assertEquals(10, procesados.size());
    }

    @Test
    void unErrorNoDetieneAlConsumidor() throws InterruptedException {
        gestor = new GestorColasDeMensajes(10);
        var segundo = new CountDownLatch(1);
        gestor.iniciar(1, s -> {
            if (s.idMensaje().equals("malo")) {
                throw new IllegalStateException("fallo simulado");
            }
            segundo.countDown();
        });

        gestor.encolar(solicitud("malo"));
        gestor.encolar(solicitud("bueno"));

        assertTrue(segundo.await(5, TimeUnit.SECONDS));
    }

    @Test
    void detenerVaciaLaColaDentroDelPlazo() {
        gestor = new GestorColasDeMensajes(100);
        List<String> procesados = new CopyOnWriteArrayList<>();
        gestor.iniciar(2, s -> {
            dormir(20);
            procesados.add(s.idMensaje());
        });
        for (int i = 0; i < 20; i++) {
            gestor.encolar(solicitud("m" + i));
        }

        gestor.detener();

        assertEquals(20, procesados.size(), "detener espera a que se procese lo pendiente");
        assertEquals(0, gestor.pendientes());
        assertFalse(gestor.encolar(solicitud("tarde")), "después de detener no se aceptan mensajes");
    }

    @Test
    void siSeVenceElPlazoSeDescartaLoPendiente() {
        gestor = new GestorColasDeMensajes(100, Duration.ofMillis(300));
        var bloqueo = new CountDownLatch(1);
        gestor.iniciar(1, s -> esperar(bloqueo)); // nunca termina hasta que lo interrumpan
        for (int i = 0; i < 5; i++) {
            gestor.encolar(solicitud("m" + i));
        }

        long inicio = System.nanoTime();
        gestor.detener();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        assertTrue(ms < 2_000, "respeta el plazo: " + ms + " ms");
        assertEquals(0, gestor.pendientes());
    }

    @Test
    void detenerEsIdempotenteYFuncionaSinIniciar() {
        gestor = new GestorColasDeMensajes(10);
        gestor.encolar(solicitud("1"));
        gestor.detener();
        gestor.detener();
        assertEquals(0, gestor.pendientes());
        assertThrows(IllegalStateException.class, () -> gestor.iniciar(1, s -> { }));
    }

    @Test
    void validaLosParametros() {
        assertThrows(IllegalArgumentException.class, () -> new GestorColasDeMensajes(0));
        gestor = new GestorColasDeMensajes(10);
        assertThrows(IllegalArgumentException.class, () -> gestor.iniciar(0, s -> { }));
        gestor.iniciar(1, s -> { });
        assertThrows(IllegalStateException.class, () -> gestor.iniciar(1, s -> { }), "no se inicia dos veces");
    }

    private static SolicitudMensajeDTO solicitud(String id) {
        return new SolicitudMensajeDTO(id, null, null, "20201001", Canal.FORO, null,
                new ContenidoDTO(TipoMensaje.TEXTO, "hola", null, null), LocalDateTime.now());
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
