package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.EventoPushDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.soporte.conexiones.GestorConexiones;
import com.universidad.mensajeria.soporte.conexiones.ICanalCliente;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocoloClienteServidorTest {

    private static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);

    /** Solo acepta 20201001 / clave123; "boom" simula un fallo interno. */
    private static final IUsuariosDisponibles USUARIOS = new IUsuariosDisponibles() {
        @Override
        public Optional<UsuarioDTO> autenticar(String codigo, String clave) {
            if ("boom".equals(codigo)) {
                throw new IllegalStateException("fallo simulado");
            }
            return ANA.codigo().equals(codigo) && "clave123".equals(clave) ? Optional.of(ANA) : Optional.empty();
        }

        @Override
        public List<UsuarioDTO> listarRegistrados() {
            return List.of(ANA);
        }

        @Override
        public Optional<UsuarioDTO> buscar(String codigo) {
            return ANA.codigo().equals(codigo) ? Optional.of(ANA) : Optional.empty();
        }

        @Override
        public boolean esAdmin(String codigo) {
            return false;
        }
    };

    private GestorConexiones gestor;
    private ProtocoloClienteServidor protocolo;
    private CanalFalso canal;

    @BeforeEach
    void preparar() {
        gestor = new GestorConexiones(5);
        protocolo = new ProtocoloClienteServidor(
                new FabricaManejadoresComando(USUARIOS, gestor, new SerializadorJSON()));
        canal = new CanalFalso();
        gestor.registrar(canal);
    }

    @Test
    void sinSesionSoloSeAceptaLogin() {
        var respuesta = protocolo.procesar(peticion(TipoComando.LISTAR_USUARIOS, Map.of()), canal);
        assertFalse(respuesta.exito());
        assertEquals("Debe iniciar sesión", respuesta.mensaje());
    }

    @Test
    void loginCorrectoAsociaElUsuario() {
        var respuesta = login("20201001", "clave123");

        assertTrue(respuesta.exito());
        assertEquals("20201001", respuesta.datos().get("codigo").asText());
        assertEquals("USUARIO", respuesta.datos().get("rol").asText());
        assertEquals(ANA, canal.getUsuario().orElseThrow());
        assertEquals(1, gestor.sesionesDe("20201001").size());
    }

    @Test
    void loginIncorrecto() {
        var respuesta = login("20201001", "otra");
        assertFalse(respuesta.exito());
        assertEquals("Credenciales inválidas", respuesta.mensaje());
        assertTrue(canal.getUsuario().isEmpty());
    }

    @Test
    void loginSinParametros() {
        assertFalse(protocolo.procesar(peticion(TipoComando.LOGIN, Map.of("codigo", "20201001")), canal).exito());
    }

    @Test
    void noSePuedeIniciarSesionDosVecesEnLaMismaConexion() {
        login("20201001", "clave123");
        assertFalse(login("20201001", "clave123").exito());
    }

    @Test
    void comandosDeFasesFuturas() {
        login("20201001", "clave123");
        var respuesta = protocolo.procesar(peticion(TipoComando.ENVIAR_FORO, Map.of()), canal);
        assertFalse(respuesta.exito());
        assertTrue(respuesta.mensaje().startsWith("Comando aún no disponible"));
    }

    @Test
    void logoutRespondeYCierraLaConexion() {
        login("20201001", "clave123");

        var respuesta = protocolo.procesar(peticion(TipoComando.LOGOUT, Map.of()), canal);

        assertTrue(respuesta.exito());
        assertEquals(List.of(respuesta), canal.enviadas, "la respuesta se encola antes del cierre");
        assertTrue(canal.cerrado);
        assertTrue(gestor.todas().isEmpty());
        assertTrue(gestor.sesionesDe("20201001").isEmpty());
    }

    @Test
    void unFalloInternoSeDevuelveComoError() {
        var respuesta = login("boom", "x");
        assertFalse(respuesta.exito());
        assertEquals("Error interno del servidor", respuesta.mensaje());
    }

    private RespuestaDTO login(String codigo, String clave) {
        return protocolo.procesar(peticion(TipoComando.LOGIN, Map.of("codigo", codigo, "clave", clave)), canal);
    }

    private static PeticionDTO peticion(TipoComando comando, Map<String, String> parametros) {
        return new PeticionDTO(UUID.randomUUID().toString(), comando, parametros, null);
    }

    /** Canal sin red: registra lo que se le envía e ignora lo enviado tras cerrarse. */
    private static final class CanalFalso implements ICanalCliente {
        private final String id = UUID.randomUUID().toString();
        private final List<RespuestaDTO> enviadas = new CopyOnWriteArrayList<>();
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
            if (!cerrado) {
                enviadas.add(r);
            }
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
