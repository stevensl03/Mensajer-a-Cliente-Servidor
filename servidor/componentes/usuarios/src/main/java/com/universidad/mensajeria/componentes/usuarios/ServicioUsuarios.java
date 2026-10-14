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
 *
 * <p>Falla al crearse si la lista tiene códigos repetidos o no tiene
 * exactamente un ADMIN. Nunca expone el hash de la contraseña: todo lo que
 * sale es {@link UsuarioDTO}.</p>
 */
public final class ServicioUsuarios implements IUsuariosDisponibles {

    private final Map<String, UsuarioRegistroDTO> catalogo;
    private final IGeneradorHash hash;
    private final UsuarioDTO administrador;

    public ServicioUsuarios(List<UsuarioRegistroDTO> registrados, IGeneradorHash hash) {
        Objects.requireNonNull(registrados, "registrados");
        this.hash = Objects.requireNonNull(hash, "hash");
        var mapa = new LinkedHashMap<String, UsuarioRegistroDTO>();
        for (UsuarioRegistroDTO u : registrados) {
            if (mapa.putIfAbsent(u.codigo(), u) != null) {
                throw new IllegalArgumentException("Código de usuario repetido: " + u.codigo());
            }
        }
        List<UsuarioDTO> admins = mapa.values().stream()
                .filter(u -> u.rol() == Rol.ADMIN)
                .map(ServicioUsuarios::aDTO)
                .toList();
        if (admins.size() != 1) {
            throw new IllegalArgumentException("Debe haber exactamente un usuario ADMIN y hay " + admins.size());
        }
        this.catalogo = Collections.unmodifiableMap(mapa);
        this.administrador = admins.get(0);
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
        return codigo == null ? Optional.empty() : Optional.ofNullable(catalogo.get(codigo)).map(ServicioUsuarios::aDTO);
    }

    @Override
    public boolean existe(String codigo) {
        return codigo != null && catalogo.containsKey(codigo);
    }

    @Override
    public UsuarioDTO administrador() {
        return administrador;
    }

    private static UsuarioDTO aDTO(UsuarioRegistroDTO u) {
        return new UsuarioDTO(u.codigo(), u.nombres(), u.apellidos(), u.programa(), u.rol());
    }
}
