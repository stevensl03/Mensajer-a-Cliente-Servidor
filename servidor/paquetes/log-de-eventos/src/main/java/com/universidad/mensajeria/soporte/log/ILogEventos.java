package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;

import java.util.List;
import java.util.function.Consumer;

/**
 * Gestor de logs de la aplicación: SLF4J, estructuras en memoria y BD.
 */
public interface ILogEventos {

    /**
     * @param t           tipo de evento.
     * @param descripcion detalle (sin contraseñas ni contenido completo).
     * @param usuario     código del usuario, o {@code null}.
     * @param ip          IP de origen, o {@code null}.
     */
    void registrar(TipoEvento t, String descripcion, String usuario, String ip);

    /** @param s sesión abierta (evento LOGIN_OK y fila en {@code conexiones_usuario}). */
    void conexion(SesionDTO s);

    /**
     * @param s      sesión cerrada.
     * @param motivo LOGOUT, DESCONEXION, CERRADA_POR_ADMIN o SERVIDOR_DETENIDO.
     */
    void desconexion(SesionDTO s, String motivo);

    /** @return los eventos que cumplen el filtro. */
    List<EventoLogDTO> consultar(FiltroLogs f);

    /** @return todos los eventos en orden. */
    List<EventoLogDTO> todos();

    /** Carga en memoria el historial persistido. */
    void cargarHistorial();

    /**
     * Registra un oyente que recibe cada evento nuevo (no el historial). La
     * fachada lo usa para avisar a las vistas; un oyente que falla no afecta a
     * los demás ni al registro.
     *
     * @param o oyente.
     */
    void suscribir(Consumer<EventoLogDTO> o);
}
