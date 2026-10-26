package com.universidad.mensajeria.cliente.servicios.usuarios;

import com.universidad.mensajeria.cliente.persistencia.FabricaRepositoriosH2;
import com.universidad.mensajeria.cliente.persistencia.ProveedorConexionH2;
import com.universidad.mensajeria.cliente.servicios.ProtocoloFalso;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.SerializadorJSON;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.UsuarioDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioUsuariosTest {

    static final String LOGIN_OK = """
            {"idSesion":"s1","ipServidor":"192.168.20.31","usuario":{"codigo":"20201001","nombres":"Ana",
             "apellidos":"Pérez","programa":"Ingeniería de Sistemas","rol":"USUARIO"}}""";

    /** En archivo, como el cliente real: una base en memoria desaparece al cerrarla. */
    @TempDir
    Path dir;

    private ProtocoloFalso protocolo;
    private ProveedorConexionH2 proveedor;
    private ServicioUsuarios servicio;

    @BeforeEach
    void preparar() {
        protocolo = new ProtocoloFalso();
        proveedor = new ProveedorConexionH2("jdbc:h2:" + dir.toAbsolutePath() + "/cliente_{codigo}", "sa", "");
        servicio = new ServicioUsuarios(protocolo, proveedor, new FabricaRepositoriosH2(proveedor).sesiones(),
                new SerializadorJSON());
    }

    @AfterEach
    void cerrar() {
        proveedor.cerrar();
    }

    @Test
    void unLoginCorrectoAbreLaBaseYRegistraLaSesionLocal() throws Exception {
        protocolo.responder(TipoComando.LOGIN, ProtocoloFalso.exito(LOGIN_OK));

        UsuarioDTO u = servicio.login("20201001", "clave123", 5000).get(5, TimeUnit.SECONDS);

        assertEquals("Ana", u.nombres());
        assertEquals(Map.of("codigo", "20201001", "clave", "clave123"), protocolo.siguiente().params());
        assertEquals(u, servicio.actual().orElseThrow());
        assertTrue(servicio.sesionLocal() > 0);
        assertEquals("192.168.20.31|5000|abierta", sesion(servicio.sesionLocal()));
    }

    @Test
    void credencialesInvalidasYCupoLlenoFallanConSuCodigoSinAbrirLaBase() {
        for (CodigoError codigo : List.of(CodigoError.CREDENCIALES_INVALIDAS, CodigoError.CUPO_LLENO)) {
            protocolo.responder(TipoComando.LOGIN, ProtocoloFalso.error(codigo, "rechazado"));

            var e = assertThrows(ExecutionException.class,
                    () -> servicio.login("20201001", "mala", 5000).get(5, TimeUnit.SECONDS));

            assertEquals(codigo, assertInstanceOf(ExcepcionServidor.class, e.getCause()).codigo());
            assertTrue(servicio.actual().isEmpty());
            assertEquals(-1, servicio.sesionLocal());
            assertThrows(IllegalStateException.class, () -> proveedor.obtenerConexion());
        }
    }

    @Test
    void logoutEnviaLogoutCierraLaSesionLocalYLaBase() throws Exception {
        protocolo.responder(TipoComando.LOGIN, ProtocoloFalso.exito(LOGIN_OK));
        protocolo.responder(TipoComando.LOGOUT, ProtocoloFalso.exito("{}"));
        servicio.login("20201001", "clave123", 5000).get(5, TimeUnit.SECONDS);
        long id = servicio.sesionLocal();

        servicio.logout();
        servicio.logout();

        assertEquals(TipoComando.LOGIN, protocolo.siguiente().comando());
        assertEquals(TipoComando.LOGOUT, protocolo.siguiente().comando());
        assertTrue(protocolo.solicitudes.isEmpty(), "el segundo logout no hace nada");
        assertTrue(servicio.actual().isEmpty());
        assertEquals(-1, servicio.sesionLocal());
        assertThrows(IllegalStateException.class, () -> proveedor.obtenerConexion());
        proveedor.abrir("20201001");
        assertEquals("192.168.20.31|5000|cerrada", sesion(id));
    }

    @Test
    void logoutSinConexionCierraIgualLoLocal() throws Exception {
        protocolo.responder(TipoComando.LOGIN, ProtocoloFalso.exito(LOGIN_OK));
        protocolo.responder(TipoComando.LOGOUT, s -> {
            throw new IllegalStateException("Desconectado del servidor");
        });
        servicio.login("20201001", "clave123", 5000).get(5, TimeUnit.SECONDS);
        long id = servicio.sesionLocal();

        servicio.logout();

        assertTrue(servicio.actual().isEmpty());
        assertThrows(IllegalStateException.class, () -> proveedor.obtenerConexion());
        proveedor.abrir("20201001");
        assertEquals("192.168.20.31|5000|cerrada", sesion(id));
    }

    @Test
    void siLaBaseLocalNoAbreSeLiberaLaSesionDelServidor() throws Exception {
        protocolo.responder(TipoComando.LOGIN, ProtocoloFalso.exito(LOGIN_OK.replace("\"codigo\":\"20201001\"",
                "\"codigo\":\"../otro\"")));

        var e = assertThrows(ExecutionException.class,
                () -> servicio.login("20201001", "clave123", 5000).get(5, TimeUnit.SECONDS));

        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals(TipoComando.LOGIN, protocolo.siguiente().comando());
        assertEquals(TipoComando.LOGOUT, protocolo.siguiente().comando());
        assertTrue(servicio.actual().isEmpty());
    }

    @Test
    void listarConvierteLosEstados() throws Exception {
        protocolo.responder(TipoComando.LISTAR_USUARIOS, ProtocoloFalso.exito("""
                {"usuarios":[{"usuario":{"codigo":"ADMIN01","nombres":"Admin","apellidos":"Sistema",
                  "programa":"TI","rol":"ADMIN"},"conectado":true,"sesiones":0}]}"""));

        List<EstadoUsuarioDTO> l = servicio.listar().get(5, TimeUnit.SECONDS);

        assertEquals(1, l.size());
        assertEquals(Rol.ADMIN, l.get(0).usuario().rol());
        assertTrue(l.get(0).conectado());
        assertFalse(servicio.actual().isPresent());
    }

    private String sesion(long id) throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT IP_SERVIDOR, PUERTO, FIN FROM SESION WHERE ID = " + id)) {
            rs.next();
            return rs.getString(1) + "|" + rs.getInt(2) + "|" + (rs.getObject(3) == null ? "abierta" : "cerrada");
        }
    }
}
