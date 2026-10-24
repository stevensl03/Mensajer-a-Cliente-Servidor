package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Sesiones activas con cupo global ({@code servidor.maxSesiones}).
 *
 * <p>El cupo cuenta TODAS las sesiones activas: un mismo usuario puede abrir
 * varias, y si ocupa las N los demás no pueden entrar. Seguro entre hilos.</p>
 */
public final class GestorConexiones implements IGestorConexiones {

    private final Semaphore cupo;
    private final ConcurrentHashMap<String, SesionActiva> sesiones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> porUsuario = new ConcurrentHashMap<>();

    /**
     * @param maxSesiones máximo de sesiones simultáneas (mayor que 0).
     */
    public GestorConexiones(int maxSesiones) {
        if (maxSesiones <= 0) {
            throw new IllegalArgumentException("maxSesiones debe ser mayor que 0: " + maxSesiones);
        }
        this.cupo = new Semaphore(maxSesiones, true);
    }

    @Override
    public boolean abrirSesion(ICanalCliente c, UsuarioDTO u) {
        Objects.requireNonNull(c, "c");
        Objects.requireNonNull(u, "u");
        if (!cupo.tryAcquire()) {
            return false;
        }
        if (sesiones.putIfAbsent(c.getId(), new SesionActiva(c, u, LocalDateTime.now())) != null) {
            cupo.release();
            throw new IllegalStateException("El canal ya tiene sesión: " + c.getId());
        }
        porUsuario.computeIfAbsent(u.codigo(), k -> ConcurrentHashMap.newKeySet()).add(c.getId());
        return true;
    }

    @Override
    public Optional<SesionDTO> cerrarSesion(String idSesion) {
        SesionActiva sesion = sesiones.remove(idSesion);
        if (sesion == null) {
            return Optional.empty();
        }
        cupo.release();
        porUsuario.computeIfPresent(sesion.usuario().codigo(), (k, ids) -> {
            ids.remove(idSesion);
            return ids.isEmpty() ? null : ids;
        });
        return Optional.of(sesion.aDTO());
    }

    @Override
    public Optional<UsuarioDTO> usuarioDe(String idSesion) {
        return Optional.ofNullable(sesiones.get(idSesion)).map(SesionActiva::usuario);
    }

    @Override
    public List<ICanalCliente> canalesDe(String codigo) {
        Set<String> ids = porUsuario.get(codigo);
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(sesiones::get).filter(Objects::nonNull).map(SesionActiva::canal).toList();
    }

    @Override
    public Collection<ICanalCliente> canalesActivos() {
        return sesiones.values().stream().map(SesionActiva::canal).toList();
    }

    @Override
    public List<SesionDTO> resumen() {
        return sesiones.values().stream()
                .sorted(Comparator.comparing(SesionActiva::desde))
                .map(SesionActiva::aDTO)
                .toList();
    }

    @Override
    public List<ConexionUsuarioDTO> conteoPorUsuario() {
        Map<String, List<SesionDTO>> porCodigo = sesiones.values().stream()
                .map(SesionActiva::aDTO)
                .collect(Collectors.groupingBy(SesionDTO::codigo, TreeMap::new, Collectors.toList()));
        return porCodigo.values().stream()
                .map(l -> new ConexionUsuarioDTO(l.get(0).codigo(), l.get(0).nombreCompleto(), l.size()))
                .toList();
    }

    @Override
    public int cuposLibres() {
        return cupo.availablePermits();
    }
}
