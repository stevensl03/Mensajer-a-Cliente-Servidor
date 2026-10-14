package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
