package com.universidad.mensajeria.inicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import com.universidad.mensajeria.persistencia.jdbc.DialectoMySQL;
import com.universidad.mensajeria.persistencia.jdbc.DialectoPostgreSQL;
import com.universidad.mensajeria.persistencia.jdbc.ProveedorConexionJDBC;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import com.universidad.mensajeria.utilerias.UtilImagen;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase 8: el servidor real (mismo ensamblado que {@code ServidorApp}) contra una
 * BD de pruebas, con clientes que hablan el protocolo §7. Recorre dos usuarios,
 * foro, privado, imagen, cupo lleno, informes, el ADMIN, LOGOUT y el apagado, y
 * al final revisa las tablas y la carpeta {@code almacen}.
 *
 * <pre>
 * mvn verify -Pintegracion -pl presentacion/inicio-servidor -am \
 *     -Dbd.motor=MYSQL -Dbd.url=jdbc:mysql://localhost:3306/mensajeria_e2e -Dbd.usuario=... -Dbd.clave=...
 * </pre>
 * <b>Usar una BD solo para pruebas:</b> se vacían todas sus tablas al empezar.
 */
@Tag("integracion")
@Timeout(120)
class ServidorPuntaAPuntaTest {

    private static final Path CONFIG = Path.of("../../config");
    private static final String ANA = "20201001";
    private static final String LUIS = "20201002";
    private static final String MARIA = "20201003";
    private static final String ADMIN = "ADMIN01";

    @TempDir
    Path dir;

    @Test
    void escenarioCompletoDelServidor() throws Exception {
        Properties bd = bdDePruebas();
        vaciarBd(bd);
        int puerto = puertoLibre();
        Path almacen = dir.resolve("almacen");
        ConfiguracionServidor cfg = configuracion(puerto, almacen, bd);

        IFachadaServidor fachada = ServidorApp.ensamblar(cfg, new SerializadorJSON());
        fachada.iniciarServidor();
        boolean apagado = false;
        try (var ana = new ClientePrueba("ana", puerto);
             var luis = new ClientePrueba("luis", puerto)) {

            // --- Conexión y LOGIN (§6.2) ---
            JsonNode info = ana.esperarPush("INFO_SERVIDOR");
            assertEquals(puerto, info.path("puerto").asInt());
            assertEquals(3, info.path("cuposLibres").asInt(), "la conexión TCP sola no ocupa cupo");
            assertEquals("NO_AUTENTICADO", ana.pedir(TipoComando.LISTAR_USUARIOS, Map.of()).path("codigo").asText());
            assertEquals("CREDENCIALES_INVALIDAS", login(ana, ANA, "mala").path("codigo").asText());
            JsonNode r = login(ana, ANA, "clave123");
            assertTrue(r.path("exito").asBoolean(), r.toString());
            assertEquals(ANA, r.path("datos").path("usuario").path("codigo").asText());
            assertFalse(r.path("datos").path("usuario").has("contrasenaHash"), "nunca se expone el hash");
            assertTrue(login(luis, LUIS, "clave456").path("exito").asBoolean());
            assertEquals(1, ana.esperarPush("USUARIO_CONECTADO", d -> LUIS.equals(d.path("codigo").asText()))
                    .path("sesiones").asInt());

            JsonNode usuarios = ana.pedir(TipoComando.LISTAR_USUARIOS, Map.of()).path("datos").path("usuarios");
            assertEquals(10, usuarios.size(), "los 10 usuarios del archivo plano son visibles");
            assertTrue(estado(usuarios, ADMIN).path("conectado").asBoolean(), "el ADMIN siempre aparece conectado");

            // --- Foro con texto (§6.3) ---
            String textoForo = "Hola comunidad, ñandú 😀";
            assertEquals("ENCOLADO", enviar(ana, TipoComando.ENVIAR_FORO, Map.of(), texto(textoForo))
                    .path("datos").path("estado").asText());
            JsonNode foro = luis.esperarPush("MENSAJE_NUEVO", d -> "FORO".equals(d.path("mensaje").path("canal").asText()))
                    .path("mensaje");
            assertEquals(ANA, foro.path("remitente").asText(), "el remitente sale de la sesión");
            assertEquals(23, foro.path("numCaracteres").asInt());
            assertEquals(4, foro.path("numPalabras").asInt());
            assertEquals(64, foro.path("hash").asText().length());
            ana.esperarPush("MENSAJE_NUEVO", d -> "FORO".equals(d.path("mensaje").path("canal").asText()));

            // --- Privado con texto ---
            enviar(luis, TipoComando.ENVIAR_PRIVADO, Map.of("destino", ANA), texto("Hola Ana, ¿revisaste el informe?"));
            ana.esperarPush("MENSAJE_NUEVO", d -> LUIS.equals(d.path("mensaje").path("remitente").asText()));
            luis.esperarPush("MENSAJE_NUEVO", d -> LUIS.equals(d.path("mensaje").path("remitente").asText()));
            assertEquals("DESTINO_INEXISTENTE",
                    enviar(luis, TipoComando.ENVIAR_PRIVADO, Map.of("destino", "noexiste"), texto("x")).path("codigo").asText());

            // --- Privado con imagen: 6 versiones (§6.3, §11) ---
            byte[] png = UtilImagen.aBytes(imagenDePrueba(), "png");
            enviar(ana, TipoComando.ENVIAR_PRIVADO, Map.of("destino", LUIS), new ContenidoDTO(TipoMensaje.IMAGEN,
                    null, "diagrama.png", Base64.getEncoder().encodeToString(png)));
            JsonNode imagen = luis.esperarPush("MENSAJE_NUEVO",
                    d -> "IMAGEN".equals(d.path("mensaje").path("tipo").asText())).path("mensaje");
            assertEquals(6, imagen.path("imagenes").size());
            assertEquals("diagrama.png", imagen.path("nombreArchivo").asText());
            JsonNode original = luis.pedir(TipoComando.OBTENER_IMAGEN,
                    Map.of("idMensaje", imagen.path("id").asText(), "etapa", "ORIGINAL")).path("datos");
            assertArrayEquals(png, Base64.getDecoder().decode(original.path("datosBase64").asText()));
            JsonNode sepia = luis.pedir(TipoComando.OBTENER_IMAGEN,
                    Map.of("idMensaje", imagen.path("id").asText(), "etapa", "SEPIA"));
            assertTrue(sepia.path("exito").asBoolean(), sepia.toString());
            assertEquals("CONTENIDO_INVALIDO", enviar(ana, TipoComando.ENVIAR_FORO, Map.of(),
                    new ContenidoDTO(TipoMensaje.IMAGEN, null, "virus.exe", "AAAA")).path("codigo").asText());

            // --- Cupo global (maxSesiones = 3) y un usuario conectado dos veces ---
            try (var ana2 = new ClientePrueba("ana2", puerto); var maria = new ClientePrueba("maria", puerto)) {
                assertTrue(login(ana2, ANA, "clave123").path("exito").asBoolean(), "Ana se conecta por segunda vez");
                assertEquals("CUPO_LLENO", login(maria, MARIA, "clave789").path("codigo").asText());
                assertTrue(maria.cerradoPorElServidor(), "con el cupo lleno se responde y se cierra el canal");

                // --- Informes (§6.5) ---
                JsonNode conectados = ana.pedir(TipoComando.INFORME, Map.of("tipo", "USUARIOS_CONECTADOS")).path("datos");
                assertEquals(ANA, conectados.path("generadoPor").asText());
                assertEquals(Map.of(ANA, 2, LUIS, 1), conteo(conectados.path("filas")));
                assertEquals(10, ana.pedir(TipoComando.INFORME, Map.of("tipo", "USUARIOS_REGISTRADOS"))
                        .path("datos").path("filas").size());
                assertEquals(3, ana.pedir(TipoComando.INFORME, Map.of("tipo", "MENSAJES"))
                        .path("datos").path("filas").size());
                JsonNode cupo = ana.pedir(TipoComando.INFORME, Map.of("tipo", "LOGS", "tipoEvento", "CUPO_LLENO"))
                        .path("datos").path("filas");
                assertEquals(1, cupo.size());
                assertEquals(MARIA, cupo.get(0).path("usuario").asText());
                assertEquals(1, ana.pedir(TipoComando.HISTORIAL, Map.of("conversacion", "FORO"))
                        .path("datos").path("mensajes").size());
                assertEquals(2, ana.pedir(TipoComando.HISTORIAL, Map.of("conversacion", LUIS))
                        .path("datos").path("mensajes").size());

                // --- El ADMIN escribe desde la vista (§6.4) ---
                fachada.publicarEnForo(texto("Aviso del ADMIN: mañana no hay clases"));
                assertEquals(ADMIN, ana2.esperarPush("MENSAJE_NUEVO",
                        d -> ADMIN.equals(d.path("mensaje").path("remitente").asText())).path("mensaje")
                        .path("remitente").asText());

                // --- LOGOUT (§6.6) ---
                assertTrue(luis.pedir(TipoComando.LOGOUT, Map.of()).path("exito").asBoolean());
                assertTrue(luis.cerradoPorElServidor(), "tras LOGOUT se cierra el canal");
                assertEquals(0, ana.esperarPush("USUARIO_DESCONECTADO", d -> LUIS.equals(d.path("codigo").asText()))
                        .path("sesiones").asInt());

                // --- Apagado ordenado: aviso a todos y cierre de sesiones ---
                ServidorApp.apagar(fachada, vistaVacia());
                apagado = true;
                assertEquals("SERVIDOR_DETENIDO", ana2.esperarPush("SESION_CERRADA").path("motivo").asText());
                assertTrue(ana.cerradoPorElServidor());
            }
        } finally {
            if (!apagado) {
                ServidorApp.apagar(fachada, vistaVacia());
            }
        }

        // --- Archivos en ./almacen (§11) ---
        assertEquals(3, contar(almacen.resolve("textos")), "un .txt por mensaje de texto (foro, privado y ADMIN)");
        assertEquals(1, contar(almacen.resolve("originales")));
        assertEquals(5, contar(almacen.resolve("filtradas")));

        // --- Filas en la BD ---
        var proveedor = new ProveedorConexionJDBC(bd, dialecto(bd));
        try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement()) {
            assertEquals(10, uno(s, "SELECT COUNT(*) FROM usuarios"), "carga inicial desde el archivo plano");
            assertEquals(4, uno(s, "SELECT COUNT(*) FROM mensajes"));
            assertEquals(3, uno(s, "SELECT COUNT(*) FROM mensajes_texto"));
            assertEquals(6, uno(s, "SELECT COUNT(*) FROM imagenes_mensaje"));
            assertEquals(0, uno(s, "SELECT COUNT(*) FROM imagenes_mensaje WHERE ruta_archivo LIKE '%\\\\%'"),
                    "las rutas se guardan con /");
            assertEquals(1, uno(s, "SELECT COUNT(*) FROM mensajes WHERE canal = 'FORO' AND remitente_codigo = '" + ADMIN + "'"));
            assertEquals(3, uno(s, "SELECT COUNT(*) FROM conexiones_usuario"), "Ana dos veces y Luis una");
            assertEquals(0, uno(s, "SELECT COUNT(*) FROM conexiones_usuario WHERE fecha_desconexion IS NULL"),
                    "todas las sesiones quedan cerradas");
            Map<String, Integer> logs = porTipo(s);
            assertEquals(Map.of(
                    "CONEXION", 4, "LOGIN_FALLIDO", 1, "LOGIN_OK", 3, "CUPO_LLENO", 1,
                    "MENSAJE_FORO", 2, "MENSAJE_PRIVADO", 2, "DESCONEXION", 3), logs);
        } finally {
            proveedor.cerrar();
        }
    }

    // --- Ayudas ---

    private static JsonNode login(ClientePrueba c, String codigo, String clave) throws IOException {
        return c.pedir(TipoComando.LOGIN, Map.of("codigo", codigo, "clave", clave));
    }

    private static JsonNode enviar(ClientePrueba c, TipoComando comando, Map<String, String> p, ContenidoDTO contenido)
            throws IOException {
        return c.pedir(comando, p, contenido);
    }

    private static ContenidoDTO texto(String t) {
        return new ContenidoDTO(TipoMensaje.TEXTO, t, null, null);
    }

    private static JsonNode estado(JsonNode usuarios, String codigo) {
        for (JsonNode u : usuarios) {
            if (codigo.equals(u.path("usuario").path("codigo").asText())) {
                return u;
            }
        }
        throw new AssertionError("no está el usuario " + codigo);
    }

    private static Map<String, Integer> conteo(JsonNode filas) {
        var m = new HashMap<String, Integer>();
        filas.forEach(f -> m.put(f.path("codigo").asText(), f.path("cantidadSesiones").asInt()));
        return m;
    }

    private static BufferedImage imagenDePrueba() {
        var img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, (x * 4) << 16 | (y * 8) << 8 | 128);
            }
        }
        return img;
    }

    private static IVista vistaVacia() {
        return new IVista() {
            @Override
            public void iniciar() {
            }

            @Override
            public void detener() {
            }
        };
    }

    private static long contar(Path carpeta) throws IOException {
        try (Stream<Path> s = Files.list(carpeta)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private static int uno(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Map<String, Integer> porTipo(Statement s) throws SQLException {
        var m = new HashMap<String, Integer>();
        try (ResultSet rs = s.executeQuery("SELECT tipo, COUNT(*) FROM logs_eventos GROUP BY tipo")) {
            while (rs.next()) {
                m.put(rs.getString(1), rs.getInt(2));
            }
        }
        return m;
    }

    // --- Entorno de la prueba ---

    private static Properties bdDePruebas() {
        String url = System.getProperty("bd.url");
        assumeTrue(url != null && !url.isBlank(), "Defina -Dbd.url para ejecutar la prueba de punta a punta");
        var bd = new Properties();
        bd.setProperty("bd.motor", System.getProperty("bd.motor", "MYSQL"));
        bd.setProperty("bd.url", url);
        bd.setProperty("bd.usuario", System.getProperty("bd.usuario", "root"));
        bd.setProperty("bd.clave", System.getProperty("bd.clave", ""));
        bd.setProperty("bd.pool", "5");
        return bd;
    }

    private static IDialectoSQL dialecto(Properties bd) {
        return "POSTGRESQL".equalsIgnoreCase(bd.getProperty("bd.motor")) ? new DialectoPostgreSQL() : new DialectoMySQL();
    }

    /** Migra la BD y la deja vacía, para que la carga inicial de usuarios vuelva a ocurrir. */
    private static void vaciarBd(Properties bd) throws SQLException {
        var proveedor = new ProveedorConexionJDBC(bd, dialecto(bd));
        try {
            proveedor.migrar();
            try (Connection c = proveedor.obtenerConexion(); Statement s = c.createStatement()) {
                for (String tabla : List.of("imagenes_mensaje", "mensajes_texto", "mensajes", "logs_eventos",
                        "conexiones_usuario", "usuarios")) {
                    s.executeUpdate("DELETE FROM " + tabla);
                }
            }
        } finally {
            proveedor.cerrar();
        }
    }

    private static int puertoLibre() throws IOException {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** La configuración del repositorio con el puerto, el cupo (3), la carpeta de archivos y la BD de la prueba. */
    private ConfiguracionServidor configuracion(int puerto, Path almacen, Properties bd) throws IOException {
        Map<String, String> cambios = Map.of(
                "servidor.puerto", String.valueOf(puerto),
                "servidor.maxSesiones", "3",
                "archivos.directorio", barras(almacen),
                "usuarios.archivoInicial", barras(CONFIG.resolve("usuarios.txt").toAbsolutePath().normalize()),
                "cache.l2.habilitada", "false");
        String servidor = Files.readAllLines(CONFIG.resolve("servidor.properties"), StandardCharsets.UTF_8).stream()
                .map(l -> {
                    int igual = l.indexOf('=');
                    String clave = igual < 0 ? "" : l.substring(0, igual).strip();
                    return cambios.containsKey(clave) ? clave + "=" + cambios.get(clave) : l;
                })
                .collect(Collectors.joining("\n"));
        Path archivoServidor = Files.writeString(dir.resolve("servidor.properties"), servidor, StandardCharsets.UTF_8);
        Path archivoBd = dir.resolve("bd.properties");
        try (var w = Files.newBufferedWriter(archivoBd, StandardCharsets.UTF_8)) {
            bd.store(w, "BD de la prueba de punta a punta");
        }
        return ConfiguracionServidor.cargar(archivoServidor, archivoBd);
    }

    /** Las barras invertidas son escapes en un .properties: se usan rutas con /. */
    private static String barras(Path p) {
        return p.toString().replace('\\', '/');
    }
}
