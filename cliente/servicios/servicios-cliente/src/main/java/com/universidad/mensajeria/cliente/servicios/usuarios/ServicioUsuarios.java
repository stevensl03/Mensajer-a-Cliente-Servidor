package com.universidad.mensajeria.cliente.servicios.usuarios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.universidad.mensajeria.cliente.persistencia.IProveedorConexionBD;
import com.universidad.mensajeria.cliente.persistencia.IRepositorioSesion;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Componente Usuarios. Tras un LOGIN correcto abre la base H2 del usuario y
 * registra la sesión local; LOGOUT la cierra (cliente §5.2 y §5.5).
 */
public final class ServicioUsuarios implements IServicioUsuarios {

    /** Espera máxima de la respuesta de LOGOUT (R-14). */
    static final Duration ESPERA_LOGOUT = Duration.ofSeconds(3);
    private static final TypeReference<UsuarioDTO> USUARIO = new TypeReference<>() { };
    private static final TypeReference<List<EstadoUsuarioDTO>> ESTADOS = new TypeReference<>() { };

    private final IProtocoloCliente protocolo;
    private final IProveedorConexionBD proveedor;
    private final IRepositorioSesion sesiones;
    private final ISerializador json;
    private volatile UsuarioDTO actual;
    private volatile long sesionLocal = -1;

    public ServicioUsuarios(IProtocoloCliente protocolo, IProveedorConexionBD proveedor,
                            IRepositorioSesion sesiones, ISerializador json) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.proveedor = Objects.requireNonNull(proveedor, "proveedor");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.json = Objects.requireNonNull(json, "json");
    }

    /** La base local se abre fuera del hilo lector de la red ({@code thenApplyAsync}). */
    @Override
    public CompletableFuture<UsuarioDTO> login(String codigo, String clave, int puerto) {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(clave, "clave");
        return protocolo.solicitar(TipoComando.LOGIN, Map.of("codigo", codigo, "clave", clave), null)
                .thenApplyAsync(r -> abrirSesion(ExcepcionServidor.exigirExito(r), puerto));
    }

    private UsuarioDTO abrirSesion(RespuestaDTO r, int puerto) {
        JsonNode datos = r.datos();
        UsuarioDTO u = json.convertir(datos.get("usuario"), USUARIO);
        String ip = datos.path("ipServidor").asText(null);
        try {
            proveedor.abrir(u.codigo());
            sesionLocal = sesiones.abrir(u, ip == null ? "desconocida" : ip, puerto);
            actual = u;
            return u;
        } catch (RuntimeException e) {
            // El servidor ya abrió la sesión: se libera su cupo antes de fallar.
            protocolo.solicitar(TipoComando.LOGOUT, null, null);
            proveedor.cerrar();
            sesionLocal = -1;
            throw new IllegalStateException("No se pudo abrir la base local: " + e.getMessage(), e);
        }
    }

    @Override
    public void logout() {
        UsuarioDTO u = actual;
        if (u == null) {
            return;
        }
        actual = null;
        try {
            protocolo.solicitar(TipoComando.LOGOUT, null, null)
                    .get(ESPERA_LOGOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // Sin conexión o sin respuesta: igual se cierra lo local.
        }
        long id = sesionLocal;
        sesionLocal = -1;
        try {
            if (id >= 0) {
                sesiones.cerrar(id);
            }
        } finally {
            proveedor.cerrar();
        }
    }

    @Override
    public CompletableFuture<List<EstadoUsuarioDTO>> listar() {
        return protocolo.solicitar(TipoComando.LISTAR_USUARIOS, null, null)
                .thenApply(r -> json.convertir(ExcepcionServidor.exigirExito(r).datos().get("usuarios"), ESTADOS));
    }

    @Override
    public Optional<UsuarioDTO> actual() {
        return Optional.ofNullable(actual);
    }

    @Override
    public long sesionLocal() {
        return sesionLocal;
    }
}
