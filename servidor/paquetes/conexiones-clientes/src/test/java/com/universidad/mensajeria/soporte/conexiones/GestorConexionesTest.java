package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestorConexionesTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    private static final UsuarioDTO LUIS = new UsuarioDTO("20201002", "Luis", "Gómez", "Civil", Rol.USUARIO);

    @Test
    void unMismoUsuarioPuedeOcuparTodoElCupo() {
        var gestor = new GestorConexiones(2);
        assertTrue(gestor.abrirSesion(new CanalFalso(), ANA));
        assertTrue(gestor.abrirSesion(new CanalFalso(), ANA));

        assertEquals(0, gestor.cuposLibres());
        assertFalse(gestor.abrirSesion(new CanalFalso(), LUIS));
        assertEquals(2, gestor.canalesDe(ANA.codigo()).size());
    }

    @Test
    void cerrarLiberaElCupoUnaSolaVez() {
        var gestor = new GestorConexiones(1);
        var a = new CanalFalso();
        gestor.abrirSesion(a, ANA);

        assertTrue(gestor.cerrarSesion(a.getId()).isPresent());
        assertTrue(gestor.cerrarSesion(a.getId()).isEmpty());

        assertTrue(gestor.abrirSesion(new CanalFalso(), LUIS));
        assertFalse(gestor.abrirSesion(new CanalFalso(), LUIS), "un cierre doble no debe liberar dos cupos");
    }

    @Test
    void resumenIncluyeUsuarioEIp() {
        var gestor = new GestorConexiones(5);
        var canal = new CanalFalso();
        gestor.abrirSesion(canal, ANA);

        SesionDTO sesion = gestor.resumen().get(0);

        assertEquals(canal.getId(), sesion.idSesion());
        assertEquals("20201001", sesion.codigo());
        assertEquals("Ana Pérez", sesion.nombreCompleto());
        assertEquals("127.0.0.1", sesion.ip());
        assertEquals(ANA, gestor.usuarioDe(canal.getId()).orElseThrow());
    }

    @Test
    void conteoPorUsuarioAgrupaLasSesionesYOrdenaPorCodigo() {
        var gestor = new GestorConexiones(5);
        gestor.abrirSesion(new CanalFalso(), LUIS);
        var deAna = new CanalFalso();
        gestor.abrirSesion(deAna, ANA);
        gestor.abrirSesion(new CanalFalso(), ANA);

        assertEquals(List.of(new ConexionUsuarioDTO("20201001", "Ana Pérez", 2),
                        new ConexionUsuarioDTO("20201002", "Luis Gómez", 1)),
                gestor.conteoPorUsuario());

        gestor.cerrarSesion(deAna.getId());
        gestor.cerrarSesion(gestor.canalesDe(LUIS.codigo()).get(0).getId());

        assertEquals(List.of(new ConexionUsuarioDTO("20201001", "Ana Pérez", 1)), gestor.conteoPorUsuario());
    }

    @Test
    void sinSesionesNoHayConteoNiCanales() {
        var gestor = new GestorConexiones(3);

        assertTrue(gestor.conteoPorUsuario().isEmpty());
        assertTrue(gestor.canalesActivos().isEmpty());
        assertTrue(gestor.canalesDe(ANA.codigo()).isEmpty());
        assertTrue(gestor.resumen().isEmpty());
    }

    @Test
    void cerrarQuitaElCanalDeLasConsultas() {
        var gestor = new GestorConexiones(3);
        var a = new CanalFalso();
        var b = new CanalFalso();
        gestor.abrirSesion(a, ANA);
        gestor.abrirSesion(b, ANA);

        gestor.cerrarSesion(a.getId());

        assertEquals(List.of(b), gestor.canalesDe(ANA.codigo()));
        assertEquals(List.of(b), List.copyOf(gestor.canalesActivos()));
        assertTrue(gestor.usuarioDe(a.getId()).isEmpty());
    }

    @Test
    void conVariosHilosNuncaSeSuperaElCupo() throws Exception {
        int cupo = 5;
        int hilos = 50;
        var gestor = new GestorConexiones(cupo);

        List<Boolean> resultados = enParalelo(hilos, i -> gestor.abrirSesion(new CanalFalso(), i % 2 == 0 ? ANA : LUIS));

        assertEquals(cupo, resultados.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, gestor.cuposLibres());
        assertEquals(cupo, gestor.resumen().size());
        assertEquals(cupo, gestor.conteoPorUsuario().stream().mapToInt(ConexionUsuarioDTO::cantidadSesiones).sum());
    }

    @Test
    void cerrarLaMismaSesionDesdeVariosHilosLiberaUnSoloCupo() throws Exception {
        var gestor = new GestorConexiones(2);
        var canal = new CanalFalso();
        gestor.abrirSesion(canal, ANA);
        gestor.abrirSesion(new CanalFalso(), LUIS);

        List<Boolean> cerradas = enParalelo(20, i -> gestor.cerrarSesion(canal.getId()).isPresent());

        assertEquals(1, cerradas.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, gestor.cuposLibres());
    }

    /** Ejecuta la tarea en {@code n} hilos que arrancan a la vez y devuelve los resultados. */
    private static <T> List<T> enParalelo(int n, TareaIndexada<T> tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            var salida = new CountDownLatch(1);
            var futuros = new ArrayList<Future<T>>();
            for (int i = 0; i < n; i++) {
                int indice = i;
                Callable<T> c = () -> {
                    salida.await();
                    return tarea.ejecutar(indice);
                };
                futuros.add(pool.submit(c));
            }
            salida.countDown();
            var resultados = new ArrayList<T>();
            for (Future<T> f : futuros) {
                resultados.add(f.get(5, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface TareaIndexada<T> {
        T ejecutar(int indice);
    }

    /** Canal sin red para las pruebas. */
    private static final class CanalFalso implements ICanalCliente {
        private final String id = UUID.randomUUID().toString();

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getIpRemota() {
            return "127.0.0.1";
        }

        @Override
        public void enviar(String json) {
        }

        @Override
        public void cerrar() {
        }

        @Override
        public boolean estaAbierto() {
            return true;
        }
    }
}
