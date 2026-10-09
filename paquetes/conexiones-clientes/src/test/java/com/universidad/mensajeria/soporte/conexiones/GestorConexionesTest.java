package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestorConexionesTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);

    @Test
    void rechazaLaConexionQueSuperaElCupo() {
        var gestor = new GestorConexiones(2);
        assertTrue(gestor.registrar(new CanalFalso()));
        assertTrue(gestor.registrar(new CanalFalso()));

        assertFalse(gestor.hayCupo());
        assertFalse(gestor.registrar(new CanalFalso()));
        assertEquals(2, gestor.todas().size());
    }

    @Test
    void cerrarLiberaElCupoUnaSolaVez() {
        var gestor = new GestorConexiones(1);
        var a = new CanalFalso();
        gestor.registrar(a);

        gestor.cerrar(a.getId());
        gestor.cerrar(a.getId());

        assertTrue(a.cerrado);
        assertTrue(gestor.registrar(new CanalFalso()));
        assertFalse(gestor.registrar(new CanalFalso()), "un cierre doble no debe liberar dos cupos");
    }

    @Test
    void unUsuarioPuedeTenerVariasSesiones() {
        var gestor = new GestorConexiones(5);
        var a = new CanalFalso();
        var b = new CanalFalso();
        gestor.registrar(a);
        gestor.registrar(b);

        gestor.asociarUsuario(a.getId(), ANA);
        gestor.asociarUsuario(b.getId(), ANA);

        assertEquals(2, gestor.sesionesDe(ANA.codigo()).size());
        assertEquals(ANA, a.getUsuario().orElseThrow());

        gestor.cerrar(a.getId());
        assertEquals(1, gestor.sesionesDe(ANA.codigo()).size());
        gestor.cerrar(b.getId());
        assertTrue(gestor.sesionesDe(ANA.codigo()).isEmpty());
    }

    @Test
    void resumenIncluyeUsuarioEIp() {
        var gestor = new GestorConexiones(5);
        var anonimo = new CanalFalso();
        var conUsuario = new CanalFalso();
        gestor.registrar(anonimo);
        gestor.registrar(conUsuario);
        gestor.asociarUsuario(conUsuario.getId(), ANA);

        var resumen = gestor.resumen();

        assertEquals(2, resumen.size());
        SesionDTO sesion = resumen.stream().filter(s -> conUsuario.getId().equals(s.idConexion())).findFirst().orElseThrow();
        assertEquals("20201001", sesion.codigo());
        assertEquals("Ana Pérez", sesion.nombreCompleto());
        assertEquals("127.0.0.1", sesion.ip());
    }

    @Test
    void asociarAUnaConexionNoRegistradaNoHaceNada() {
        var gestor = new GestorConexiones(1);
        gestor.asociarUsuario("no-existe", ANA);
        assertTrue(gestor.sesionesDe(ANA.codigo()).isEmpty());
    }

    /** Canal sin red para las pruebas. */
    private static final class CanalFalso implements ICanalCliente {
        private final String id = UUID.randomUUID().toString();
        private volatile UsuarioDTO usuario;
        private volatile boolean cerrado;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getIpRemota() {
            return "127.0.0.1";
        }

        @Override
        public Optional<UsuarioDTO> getUsuario() {
            return Optional.ofNullable(usuario);
        }

        @Override
        public void asociar(UsuarioDTO u) {
            usuario = u;
        }

        @Override
        public void enviar(RespuestaDTO r) {
        }

        @Override
        public void push(EventoPushDTO e) {
        }

        @Override
        public void cerrar() {
            cerrado = true;
        }
    }
}
