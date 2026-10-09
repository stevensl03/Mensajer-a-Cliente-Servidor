package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Componente "Usuarios": autentica comparando el hash SHA-256 de la clave
 * recibida con el almacenado. Nunca guarda ni registra la clave en claro.
 */
public final class ServicioUsuarios implements IUsuariosDisponibles {

    private final IRepositorioUsuarios repositorio;
    private final IGeneradorHash hash;

    public ServicioUsuarios(IRepositorioUsuarios repositorio, IGeneradorHash hash) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    @Override
    public Optional<UsuarioDTO> autenticar(String codigo, String clave) {
        if (codigo == null || clave == null) {
            return Optional.empty();
        }
        byte[] recibido = hash.generar(clave.getBytes(StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.US_ASCII);
        return repositorio.buscarPorCodigo(codigo)
                .filter(u -> MessageDigest.isEqual(recibido,
                        u.contrasenaHash().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                .map(ServicioUsuarios::aDTO);
    }

    @Override
    public List<UsuarioDTO> listarRegistrados() {
        return repositorio.listarTodos().stream().map(ServicioUsuarios::aDTO).toList();
    }

    @Override
    public Optional<UsuarioDTO> buscar(String codigo) {
        return repositorio.buscarPorCodigo(codigo).map(ServicioUsuarios::aDTO);
    }

    @Override
    public boolean esAdmin(String codigo) {
        return buscar(codigo).map(u -> u.rol() == Rol.ADMIN).orElse(false);
    }

    private static UsuarioDTO aDTO(UsuarioRegistroDTO u) {
        return new UsuarioDTO(u.codigo(), u.nombres(), u.apellidos(), u.programa(), u.rol());
    }
}
