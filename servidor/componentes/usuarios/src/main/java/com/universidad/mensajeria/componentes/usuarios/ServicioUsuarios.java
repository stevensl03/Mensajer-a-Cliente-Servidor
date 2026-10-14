package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Clase principal del componente Usuarios: autentica comparando el SHA-256 de
 * la clave recibida con el del catálogo en memoria. No conoce la persistencia:
 * recibe la lista ya cargada.
 */
public final class ServicioUsuarios implements IUsuariosDisponibles {

    private final Map<String, UsuarioRegistroDTO> catalogo;
    private final IGeneradorHash hash;

    public ServicioUsuarios(List<UsuarioRegistroDTO> registrados, IGeneradorHash hash) {
        Objects.requireNonNull(registrados, "registrados");
        this.hash = Objects.requireNonNull(hash, "hash");
        var mapa = new LinkedHashMap<String, UsuarioRegistroDTO>();
        registrados.forEach(u -> mapa.put(u.codigo(), u));
        this.catalogo = Collections.unmodifiableMap(mapa);
    }

    @Override
    public Optional<UsuarioDTO> autenticar(String codigo, String clave) {
        if (codigo == null || clave == null) {
            return Optional.empty();
        }
        byte[] recibido = hash.generar(clave.getBytes(StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.US_ASCII);
        return Optional.ofNullable(catalogo.get(codigo))
                .filter(u -> MessageDigest.isEqual(recibido,
                        u.contrasenaHash().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                .map(ServicioUsuarios::aDTO);
    }

    @Override
    public List<UsuarioDTO> listarRegistrados() {
        return catalogo.values().stream().map(ServicioUsuarios::aDTO).toList();
    }

    @Override
    public Optional<UsuarioDTO> buscar(String codigo) {
        return Optional.ofNullable(catalogo.get(codigo)).map(ServicioUsuarios::aDTO);
    }

    @Override
    public boolean existe(String codigo) {
        return codigo != null && catalogo.containsKey(codigo);
    }

    @Override
    public UsuarioDTO administrador() {
        return catalogo.values().stream()
                .filter(u -> u.rol() == Rol.ADMIN)
                .findFirst()
                .map(ServicioUsuarios::aDTO)
                .orElseThrow(() -> new IllegalStateException("No hay un usuario con rol ADMIN"));
    }

    private static UsuarioDTO aDTO(UsuarioRegistroDTO u) {
        return new UsuarioDTO(u.codigo(), u.nombres(), u.apellidos(), u.programa(), u.rol());
    }
}
