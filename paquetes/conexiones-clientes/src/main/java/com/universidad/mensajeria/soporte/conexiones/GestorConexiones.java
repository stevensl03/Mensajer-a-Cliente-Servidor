package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Conexiones activas con cupo global ({@code servidor.maxSesiones}).
 *
 * <p>El cupo cuenta TODAS las sesiones activas: un mismo usuario puede abrir
 * varias, y si ocupa las N los demás no pueden entrar. Seguro entre hilos.</p>
 */
public final class GestorConexiones implements IGestorConexiones {

    private record Registro(ICanalCliente canal, LocalDateTime desde) {
    }

    private final int maxSesiones;
    private final Semaphore cupo;
    private final Map<String, Registro> conexiones = new ConcurrentHashMap<>();
    private final Map<String, Set<ICanalCliente>> porUsuario = new ConcurrentHashMap<>();

    /**
     * @param maxSesiones máximo de sesiones simultáneas (mayor que 0).
     */
    public GestorConexiones(int maxSesiones) {
        if (maxSesiones <= 0) {
            throw new IllegalArgumentException("maxSesiones debe ser mayor que 0: " + maxSesiones);
        }
        this.maxSesiones = maxSesiones;
        this.cupo = new Semaphore(maxSesiones);
    }

    /** @return el máximo de sesiones configurado. */
    public int getMaxSesiones() {
        return maxSesiones;
    }

    @Override
    public boolean registrar(ICanalCliente c) {
        Objects.requireNonNull(c, "c");
        if (!cupo.tryAcquire()) {
            return false;
        }
        if (conexiones.putIfAbsent(c.getId(), new Registro(c, LocalDateTime.now())) != null) {
            cupo.release();
            throw new IllegalStateException("La conexión ya estaba registrada: " + c.getId());
        }
        return true;
    }

    @Override
    public void asociarUsuario(String id, UsuarioDTO u) {
        Objects.requireNonNull(u, "u");
        Registro registro = conexiones.get(id);
        if (registro == null) {
            return;
        }
        ICanalCliente canal = registro.canal();
        canal.getUsuario()
                .filter(anterior -> !anterior.codigo().equals(u.codigo()))
                .ifPresent(anterior -> quitarDeUsuario(anterior.codigo(), canal));
        canal.asociar(u);
        porUsuario.computeIfAbsent(u.codigo(), k -> ConcurrentHashMap.newKeySet()).add(canal);
        if (!conexiones.containsKey(id)) {
            // Se cerró mientras se asociaba: no dejar referencias huérfanas.
            quitarDeUsuario(u.codigo(), canal);
        }
    }

    @Override
    public void cerrar(String id) {
        Registro registro = conexiones.remove(id);
        if (registro == null) {
            return;
        }
        cupo.release();
        registro.canal().getUsuario().ifPresent(u -> quitarDeUsuario(u.codigo(), registro.canal()));
        registro.canal().cerrar();
    }

    @Override
    public List<ICanalCliente> sesionesDe(String codigo) {
        Set<ICanalCliente> canales = porUsuario.get(codigo);
        return canales == null ? List.of() : List.copyOf(canales);
    }

    @Override
    public Collection<ICanalCliente> todas() {
        return conexiones.values().stream().map(Registro::canal).toList();
    }

    @Override
    public List<SesionDTO> resumen() {
        return conexiones.values().stream()
                .sorted(Comparator.comparing(Registro::desde))
                .map(GestorConexiones::aSesion)
                .toList();
    }

    @Override
    public boolean hayCupo() {
        return cupo.availablePermits() > 0;
    }

    private void quitarDeUsuario(String codigo, ICanalCliente canal) {
        porUsuario.computeIfPresent(codigo, (k, canales) -> {
            canales.remove(canal);
            return canales.isEmpty() ? null : canales;
        });
    }

    private static SesionDTO aSesion(Registro registro) {
        ICanalCliente canal = registro.canal();
        UsuarioDTO u = canal.getUsuario().orElse(null);
        return new SesionDTO(canal.getId(),
                u == null ? null : u.codigo(),
                u == null ? null : u.nombres() + " " + u.apellidos(),
                canal.getIpRemota(),
                registro.desde());
    }
}
