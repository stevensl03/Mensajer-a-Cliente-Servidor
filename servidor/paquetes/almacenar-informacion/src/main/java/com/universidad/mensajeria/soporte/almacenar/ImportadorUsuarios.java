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
 * <p>Formato: UTF-8, separador {@code ;} y la primera línea es el encabezado
 * {@code codigo;nombres;apellidos;contrasena;programa;rol}. Se ignoran las
 * líneas vacías y los comentarios ({@code #}). Las contraseñas se devuelven
 * como hash SHA-256.</p>
 *
 * <p>Si una línea está mal formada, un código se repite o no hay exactamente
 * un ADMIN, el arranque se detiene con un mensaje que indica el número de
 * línea (nunca la contraseña).</p>
 */
public final class ImportadorUsuarios {

    private static final Logger LOG = LoggerFactory.getLogger(ImportadorUsuarios.class);
    private static final String ENCABEZADO = "codigo;nombres;apellidos;contrasena;programa;rol";
    private static final int CAMPOS = 6;

    private final Path archivo;
    private final IGeneradorHash hash;

    public ImportadorUsuarios(Path archivo, IGeneradorHash hash) {
        this.archivo = Objects.requireNonNull(archivo, "archivo");
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    /**
     * @return los usuarios del archivo, en orden.
     * @throws UncheckedIOException  si el archivo no se puede leer.
     * @throws IllegalStateException si el contenido no es válido.
     */
    public List<UsuarioRegistroDTO> leer() {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de usuarios " + archivo, e);
        }
        if (lineas.isEmpty() || !lineas.get(0).replace("﻿", "").strip().equalsIgnoreCase(ENCABEZADO)) {
            throw new IllegalStateException(archivo + ", línea 1: se esperaba el encabezado " + ENCABEZADO);
        }
        Map<String, UsuarioRegistroDTO> usuarios = new LinkedHashMap<>();
        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i).strip();
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }
            UsuarioRegistroDTO u;
            try {
                u = parsear(linea);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(archivo + ", línea " + (i + 1) + ": " + e.getMessage(), e);
            }
            if (usuarios.putIfAbsent(u.codigo(), u) != null) {
                throw new IllegalStateException(archivo + ", línea " + (i + 1) + ": código repetido " + u.codigo());
            }
        }
        long admins = usuarios.values().stream().filter(u -> u.rol() == Rol.ADMIN).count();
        if (admins != 1) {
            throw new IllegalStateException(archivo + ": debe haber exactamente un usuario ADMIN y hay " + admins);
        }
        LOG.info("Leídos {} usuarios desde {}", usuarios.size(), archivo);
        return List.copyOf(usuarios.values());
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
