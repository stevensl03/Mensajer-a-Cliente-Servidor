package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pruebas contra una BD real. Se excluyen de {@code mvn verify}; se ejecutan con:
 * <pre>
 * mvn verify -Pintegracion -pl paquetes/persistencia -am \
 *     -Dbd.motor=MYSQL -Dbd.url=jdbc:mysql://localhost:3306/mensajeria_test \
 *     -Dbd.usuario=... -Dbd.clave=...
 * </pre>
 * <b>Usar una base de datos de pruebas:</b> se borran todas las filas antes de cada prueba.
 */
@Tag("integracion")
class PersistenciaIntegracionTest {

    private static final LocalDateTime AHORA = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

    private static ProveedorConexionJDBC proveedor;
    private static FabricaRepositoriosJDBC repos;

    @TempDir
    static Path archivos;

    @BeforeAll
    static void conectar() {
        String url = System.getProperty("bd.url");
        assumeTrue(url != null && !url.isBlank(), "Defina -Dbd.url para ejecutar las pruebas de integración");
        var bd = new Properties();
        bd.setProperty("bd.url", url);
        bd.setProperty("bd.usuario", System.getProperty("bd.usuario", "root"));
        bd.setProperty("bd.clave", System.getProperty("bd.clave", ""));
        bd.setProperty("bd.pool", "4");
        IDialectoSQL dialecto = "POSTGRESQL".equalsIgnoreCase(System.getProperty("bd.motor", "MYSQL"))
                ? new DialectoPostgreSQL() : new DialectoMySQL();
        proveedor = new ProveedorConexionJDBC(bd, dialecto);
        proveedor.migrar();
        proveedor.migrar(); // idempotente
        repos = new FabricaRepositoriosJDBC(proveedor, dialecto, archivos);
    }

    @AfterAll
    static void cerrar() {
        if (proveedor != null) {
            proveedor.cerrar();
        }
    }

    @BeforeEach
    void limpiar() throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement()) {
            for (String tabla : List.of("imagenes_mensaje", "mensajes_texto", "mensajes", "logs_eventos",
                    "conexiones_usuario", "usuarios")) {
                s.executeUpdate("DELETE FROM " + tabla);
            }
        }
    }

    @Test
    void usuarios() {
        assertEquals(0, repos.usuarios().contar());
        var ana = new UsuarioRegistroDTO("20201001", "Ana", "Pérez", "a".repeat(64), "Ingeniería de Sistemas", Rol.USUARIO);
        var admin = new UsuarioRegistroDTO("ADMIN01", "Servidor", "Administrador", "b".repeat(64), "Administración", Rol.ADMIN);

        repos.usuarios().guardarTodos(List.of(ana, admin));

        assertEquals(2, repos.usuarios().contar());
        assertEquals(List.of(ana, admin), repos.usuarios().listarTodos());
    }

    @Test
    void conexionesYHuerfanas() throws SQLException {
        repos.conexiones().abrir(new SesionDTO("s1", "20201001", "Ana Pérez", "10.0.0.5", AHORA));
        repos.conexiones().abrir(new SesionDTO("s2", "20201001", "Ana Pérez", "10.0.0.6", AHORA));
        repos.conexiones().cerrar("s1", AHORA.plusMinutes(1));

        assertEquals(1, repos.conexiones().cerrarHuerfanas(AHORA.plusMinutes(2)));
        assertEquals(0, contar("SELECT COUNT(*) FROM conexiones_usuario WHERE fecha_desconexion IS NULL"));
    }

    @Test
    void mensajeDeTextoYDeImagenIdaYVuelta() {
        var texto = new MensajeDTO(UUID.randomUUID().toString(), TipoMensaje.TEXTO, Canal.PRIVADO, "20201002",
                "20201001", AHORA, "c".repeat(64), "Hola, ñandú", 11, 2, null, List.of());
        var imagen = new MensajeDTO(UUID.randomUUID().toString(), TipoMensaje.IMAGEN, Canal.FORO, "20201001", null,
                AHORA.plusSeconds(1), "d".repeat(64), null, 0, 0, "foto.png", versiones());

        repos.mensajes().guardar(texto);
        repos.mensajes().guardar(imagen);

        List<MensajeDTO> leidos = repos.mensajes().listarTodos();
        assertEquals(List.of(texto, imagen), leidos);
        assertEquals("foto.png", leidos.get(1).nombreArchivo());
    }

    @Test
    void siFallaUnaImagenNoQuedaNadaDelMensaje() throws SQLException {
        var repetidas = new ArrayList<>(versiones());
        repetidas.add(repetidas.get(1)); // (mensaje_id, etapa) duplicado
        var mensaje = new MensajeDTO("m-falla", TipoMensaje.IMAGEN, Canal.FORO, "20201001", null, AHORA,
                "d".repeat(64), null, 0, 0, "foto.png", repetidas);

        assertThrows(IllegalStateException.class, () -> repos.mensajes().guardar(mensaje));

        assertEquals(0, contar("SELECT COUNT(*) FROM mensajes"));
        assertEquals(0, contar("SELECT COUNT(*) FROM imagenes_mensaje"));
    }

    @Test
    void logs() {
        var e1 = new EventoLogDTO(UUID.randomUUID().toString(), TipoEvento.LOGIN_OK, "LOGIN_OK", "20201001",
                "10.0.0.5", AHORA);
        var e2 = new EventoLogDTO(UUID.randomUUID().toString(), TipoEvento.ERROR, "fallo", null, null,
                AHORA.plusSeconds(1));

        repos.logs().guardar(e1);
        repos.logs().guardar(e2);

        assertEquals(List.of(e1, e2), repos.logs().listarTodos());
        assertNull(repos.logs().listarTodos().get(1).usuario());
    }

    @Test
    void laBdRechazaUnPrivadoSinDestinatario() {
        var invalido = new MensajeDTO("m-x", TipoMensaje.TEXTO, Canal.FORO, "a", null, AHORA, "h", "t", 1, 1,
                null, List.of());
        repos.mensajes().guardar(invalido);
        assertThrows(SQLException.class, () -> ejecutar(
                "UPDATE mensajes SET canal = 'PRIVADO' WHERE id = 'm-x'"));
    }

    private static List<ImagenVersionDTO> versiones() {
        var lista = new ArrayList<ImagenVersionDTO>();
        for (TipoFiltro etapa : TipoFiltro.values()) {
            String nombre = etapa == TipoFiltro.ORIGINAL ? "foto.png" : "m_" + etapa.ordinal() + "_" + etapa + ".png";
            lista.add(new ImagenVersionDTO(etapa.ordinal(), etapa, nombre, "./almacen/x/" + nombre,
                    100L + etapa.ordinal(), Integer.toHexString(etapa.ordinal()).repeat(64).substring(0, 64)));
        }
        return lista;
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void ejecutar(String sql) throws SQLException {
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}
