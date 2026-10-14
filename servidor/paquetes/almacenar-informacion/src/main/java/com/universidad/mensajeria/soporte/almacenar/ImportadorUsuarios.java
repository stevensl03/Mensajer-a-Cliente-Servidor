package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.IGeneradorHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Carga inicial de usuarios desde el archivo plano ({@code usuarios.txt}).
 *
 * <p>Formato: {@code codigo;nombres;apellidos;contrasena;programa;rol}, con una
 * cabecera. Se ignoran líneas vacías y comentarios ({@code #}). Las
 * contraseñas se devuelven como hash SHA-256.</p>
 */
public final class ImportadorUsuarios {

    private static final Logger LOG = LoggerFactory.getLogger(ImportadorUsuarios.class);
    private static final int CAMPOS = 6;

    private final Path archivo;
    private final IGeneradorHash hash;

    public ImportadorUsuarios(Path archivo, IGeneradorHash hash) {
        this.archivo = Objects.requireNonNull(archivo, "archivo");
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /**
     * @return los usuarios válidos del archivo, en orden.
     * @throws UncheckedIOException si el archivo no se puede leer.
     */
    public List<UsuarioRegistroDTO> leer() {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de usuarios " + archivo, e);
        }
        Map<String, UsuarioRegistroDTO> usuarios = new LinkedHashMap<>();
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i).strip();
            if (linea.isEmpty() || linea.startsWith("#") || esCabecera(linea)) {
                continue;
            }
            try {
                UsuarioRegistroDTO u = parsear(linea);
                if (usuarios.put(u.codigo(), u) != null) {
                    LOG.warn("Línea {} de {}: código {} repetido, se conserva el último", i + 1, archivo, u.codigo());
                }
            } catch (IllegalArgumentException e) {
                // No se registra la línea: contiene una contraseña.
                LOG.warn("Línea {} de {} omitida: {}", i + 1, archivo, e.getMessage());
            }
        }
        LOG.info("Leídos {} usuarios desde {}", usuarios.size(), archivo);
        return List.copyOf(usuarios.values());
    }

    private static boolean esCabecera(String linea) {
        return linea.toLowerCase(Locale.ROOT).startsWith("codigo;");
    }

    private UsuarioRegistroDTO parsear(String linea) {
        String[] campos = linea.split(";", -1);
        if (campos.length != CAMPOS) {
            throw new IllegalArgumentException("se esperaban " + CAMPOS + " campos y hay " + campos.length);
        }
        for (int i = 0; i < campos.length; i++) {
            campos[i] = campos[i].strip();
            if (campos[i].isEmpty()) {
                throw new IllegalArgumentException("el campo " + (i + 1) + " está vacío");
            }
        }
        Rol rol;
        try {
            rol = Rol.valueOf(campos[5].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("rol desconocido: " + campos[5]);
        }
        String contrasenaHash = hash.generar(campos[3].getBytes(StandardCharsets.UTF_8));
        return new UsuarioRegistroDTO(campos[0], campos[1], campos[2], contrasenaHash, campos[4], rol);
    }
}
