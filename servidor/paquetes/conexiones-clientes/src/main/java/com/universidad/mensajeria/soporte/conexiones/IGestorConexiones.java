package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.ConexionUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Sesiones activas en memoria con cupo global ({@code servidor.maxSesiones}).
 * No persiste nada.
 */
public interface IGestorConexiones {

    /**
     * @param c canal del cliente.
     * @param u usuario autenticado.
     * @return {@code false} si el cupo está lleno.
     */
    boolean abrirSesion(ICanalCliente c, UsuarioDTO u);

    /**
     * Libera el cupo una sola vez (idempotente).
     *
     * @param idSesion sesión a cerrar.
     * @return la sesión cerrada, o vacío si no existía.
     */
    Optional<SesionDTO> cerrarSesion(String idSesion);

    Optional<UsuarioDTO> usuarioDe(String idSesion);

    /** @return los canales con sesión del usuario. */
    List<ICanalCliente> canalesDe(String codigo);

    /** @return todos los canales con sesión. */
    Collection<ICanalCliente> canalesActivos();

    /** @return las sesiones activas, de la más antigua a la más reciente. */
    List<SesionDTO> resumen();

    /** @return cuántas sesiones tiene cada usuario conectado, ordenado por código. */
    List<ConexionUsuarioDTO> conteoPorUsuario();

    int cuposLibres();
}
