package com.universidad.mensajeria.cliente.servicios.usuarios;

import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** LOGIN, LOGOUT y LISTAR_USUARIOS; tras el login abre la base local y registra la sesión. */
public interface IServicioUsuarios {

    /**
     * @param codigo código institucional.
     * @param clave  contraseña (no se guarda ni se registra).
     * @param puerto puerto del servidor, para la sesión local (D-15).
     * @return el usuario autenticado. Falla con
     *         {@link com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor} si el servidor
     *         lo rechaza ({@code CREDENCIALES_INVALIDAS}, {@code CUPO_LLENO}…).
     */
    CompletableFuture<UsuarioDTO> login(String codigo, String clave, int puerto);

    /**
     * Envía LOGOUT (espera acotada), cierra la sesión local y la base. Sirve
     * también tras una desconexión: si el LOGOUT falla, igual cierra lo local.
     */
    void logout();

    /** @return todos los usuarios registrados con su estado de conexión. */
    CompletableFuture<List<EstadoUsuarioDTO>> listar();

    /** @return el usuario de la sesión, si hay una. */
    Optional<UsuarioDTO> actual();

    /** @return el id de la sesión local, o {@code -1} si no hay sesión. */
    long sesionLocal();
}
