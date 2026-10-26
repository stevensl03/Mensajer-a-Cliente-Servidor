package com.universidad.mensajeria.cliente.comunicacion.tcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConexionTCPTest {

    private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();

    private ServidorFalso servidor;
    private ConexionTCP conexion;
    private ReceptorGrabador receptor;

    @BeforeEach
    void preparar() throws IOException {
        servidor = new ServidorFalso();
        conexion = new ConexionTCP(1024);
        receptor = new ReceptorGrabador();
    }

    @AfterEach
    void limpiar() throws IOException {
        conexion.cerrar();
        servidor.close();
    }

    @Test
    void recibeTramasDelServidorEnUtf8() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();

        par.enviar("{\"evento\":\"INFO_SERVIDOR\"}");
        par.enviar("{\"texto\":\"Ñandú · 😀\"}");

        assertEquals("{\"evento\":\"INFO_SERVIDOR\"}", receptor.siguiente());
        assertEquals("{\"texto\":\"Ñandú · 😀\"}", receptor.siguiente());
        assertTrue(conexion.estaConectada());
        assertEquals(HOST, conexion.getIpServidor());
    }

    @Test
    void enviaEnOrdenYSinMezclarTramasDesdeVariosHilos() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();
        int hilos = 4;
        int porHilo = 50;
        var listos = new CountDownLatch(hilos);
        List<Thread> productores = new ArrayList<>();
        for (int h = 0; h < hilos; h++) {
            int id = h;
            productores.add(Thread.ofPlatform().start(() -> {
                for (int i = 0; i < porHilo; i++) {
                    conexion.enviar("{\"h\":" + id + ",\"i\":" + i + "}");
                }
                listos.countDown();
            }));
        }
        listos.await();

        Set<String> leidos = new HashSet<>();
        int[] ultimoPorHilo = {-1, -1, -1, -1};
        for (int n = 0; n < hilos * porHilo; n++) {
            String json = par.leer();
            leidos.add(json);
            int h = json.charAt(5) - '0';
            int i = Integer.parseInt(json.substring(json.indexOf("\"i\":") + 4, json.length() - 1));
            assertTrue(i > ultimoPorHilo[h], "orden por hilo: " + json);
            ultimoPorHilo[h] = i;
        }
        assertEquals(hilos * porHilo, leidos.size());
    }

    @Test
    void siElServidorCierraAvisaUnaSolaVez() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        servidor.aceptar().cerrar();

        assertEquals(ConexionTCP.MOTIVO_SERVIDOR, receptor.desconexion());
        Thread.sleep(200);
        assertEquals(1, receptor.avisos.get());
        assertFalse(conexion.estaConectada());
        assertThrows(IllegalStateException.class, () -> conexion.enviar("{}"));
    }

    @Test
    void unaTramaInvalidaCierraLaConexion() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();

        par.enviarLongitud(5_000);

        String motivo = receptor.desconexion();
        assertTrue(motivo.startsWith("Trama inválida"), motivo);
        assertFalse(conexion.estaConectada());
        assertTrue(par.finDeFlujo());
    }

    @Test
    void cerrarEntregaLoEncoladoYAvisaComoCierreDelCliente() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();
        for (int i = 0; i < 100; i++) {
            conexion.enviar("{\"n\":" + i + "}");
        }

        conexion.cerrar();

        for (int i = 0; i < 100; i++) {
            assertEquals("{\"n\":" + i + "}", par.leer());
        }
        assertTrue(par.finDeFlujo());
        assertEquals(ConexionTCP.MOTIVO_CLIENTE, receptor.desconexion());
        conexion.cerrar();
        Thread.sleep(200);
        assertEquals(1, receptor.avisos.get());
    }

    @Test
    void sePuedeReconectarDespuesDeCerrar() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var primero = servidor.aceptar();
        conexion.cerrar();
        assertEquals(ConexionTCP.MOTIVO_CLIENTE, receptor.desconexion());
        assertTrue(primero.finDeFlujo());

        var segundoReceptor = new ReceptorGrabador();
        conexion.conectar(HOST, servidor.puerto(), segundoReceptor);
        var segundo = servidor.aceptar();
        conexion.enviar("{\"otra\":true}");
        segundo.enviar("{\"hola\":2}");

        assertEquals("{\"otra\":true}", segundo.leer());
        assertEquals("{\"hola\":2}", segundoReceptor.siguiente());
        assertTrue(conexion.estaConectada());
        assertEquals(0, segundoReceptor.avisos.get());
        assertEquals(1, receptor.avisos.get());
    }

    @Test
    void sePuedeReconectarDespuesDeUnaCaida() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        servidor.aceptar().cerrar();
        receptor.desconexion();

        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();
        par.enviar("{\"ok\":1}");

        assertEquals("{\"ok\":1}", receptor.siguiente());
    }

    @Test
    void elAvisoDeLaConexionAnteriorOcurreAntesDeAbrirLaSiguiente() throws Exception {
        var termino = new java.util.concurrent.atomic.AtomicBoolean(false);
        var lento = new ReceptorGrabador() {
            @Override
            public void alDesconectar(String motivo) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                termino.set(true);
                super.alDesconectar(motivo);
            }
        };
        conexion.conectar(HOST, servidor.puerto(), lento);
        servidor.aceptar();
        conexion.cerrar();

        conexion.conectar(HOST, servidor.puerto(), receptor);

        assertTrue(termino.get(), "conectar() volvió antes del aviso de la conexión anterior");
        servidor.aceptar();
        assertTrue(conexion.estaConectada());
    }

    @Test
    void sePuedeReconectarDesdeElPropioAvisoDeDesconexion() throws Exception {
        var reconectado = new CountDownLatch(1);
        var reconecta = new ReceptorGrabador() {
            @Override
            public void alDesconectar(String motivo) {
                super.alDesconectar(motivo);
                if (avisos.get() == 1) {
                    conexion.conectar(HOST, servidor.puerto(), this);
                    reconectado.countDown();
                }
            }
        };
        conexion.conectar(HOST, servidor.puerto(), reconecta);
        servidor.aceptar().cerrar();

        assertTrue(reconectado.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var par = servidor.aceptar();
        par.enviar("{\"de\":\"nuevo\"}");
        assertEquals("{\"de\":\"nuevo\"}", reconecta.siguiente());
    }

    @Test
    void noConectaDosVeces() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        servidor.aceptar();

        assertThrows(IllegalStateException.class, () -> conexion.conectar(HOST, servidor.puerto(), receptor));
    }

    @Test
    void unPuertoSinServidorLanzaUncheckedIOException() throws IOException {
        int libre;
        try (var s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            libre = s.getLocalPort();
        }

        var e = assertThrows(UncheckedIOException.class, () -> conexion.conectar(HOST, libre, receptor));

        assertTrue(e.getMessage().contains(HOST + ":" + libre), e.getMessage());
        assertFalse(conexion.estaConectada());
        assertNull(conexion.getIpServidor());
        assertEquals(0, receptor.avisos.get());
    }

    @Test
    void unMensajeDemasiadoGrandeSeRechazaYLaConexionSigue() throws Exception {
        conexion.conectar(HOST, servidor.puerto(), receptor);
        var par = servidor.aceptar();

        assertThrows(IllegalArgumentException.class, () -> conexion.enviar("x".repeat(1025)));
        assertThrows(IllegalArgumentException.class, () -> conexion.enviar(""));
        conexion.enviar("{\"sigue\":true}");

        assertEquals("{\"sigue\":true}", par.leer());
        assertTrue(conexion.estaConectada());
    }

    @Test
    void unErrorDelReceptorNoDetieneLaLectura() throws Exception {
        var fallaUnaVez = new ReceptorGrabador() {
            boolean fallo;

            @Override
            public void alRecibir(String json) {
                if (!fallo) {
                    fallo = true;
                    throw new IllegalStateException("fallo de prueba");
                }
                super.alRecibir(json);
            }
        };
        conexion.conectar(HOST, servidor.puerto(), fallaUnaVez);
        var par = servidor.aceptar();

        par.enviar("{\"primero\":1}");
        par.enviar("{\"segundo\":2}");

        assertEquals("{\"segundo\":2}", fallaUnaVez.siguiente());
        assertTrue(conexion.estaConectada());
    }

    @Test
    void sinConectarEnviarFallaYCerrarNoHaceNada() {
        assertThrows(IllegalStateException.class, () -> conexion.enviar("{}"));
        conexion.cerrar();
        assertFalse(conexion.estaConectada());
        assertNull(conexion.getIpServidor());
    }
}
