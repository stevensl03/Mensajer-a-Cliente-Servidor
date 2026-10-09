package com.universidad.mensajeria.persistencia;

import com.universidad.mensajeria.dto.UsuarioRegistroDTO;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio de usuarios en memoria.
 *
 * <p>Clase de transición (no está en el diagrama): permite autenticar sin
 * MySQL hasta que exista {@code RepositorioUsuariosJDBC}.</p>
 */
public final class RepositorioUsuariosMemoria implements IRepositorioUsuarios {

    private final Map<String, UsuarioRegistroDTO> usuarios = new ConcurrentHashMap<>();

    @Override
    public Optional<UsuarioRegistroDTO> buscarPorCodigo(String c) {
        return c == null ? Optional.empty() : Optional.ofNullable(usuarios.get(c));
    }

    @Override
    public List<UsuarioRegistroDTO> listarTodos() {
        return usuarios.values().stream()
                .sorted(Comparator.comparing(UsuarioRegistroDTO::codigo))
                .toList();
    }

    @Override
    public void guardarTodos(List<UsuarioRegistroDTO> l) {
        l.forEach(u -> usuarios.put(u.codigo(), u));
    }
}
