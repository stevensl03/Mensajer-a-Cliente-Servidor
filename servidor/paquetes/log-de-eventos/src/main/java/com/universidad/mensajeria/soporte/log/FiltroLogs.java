package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.TipoEvento;

import java.time.LocalDateTime;

/**
 * Filtro de consulta de logs. Campos nulos = sin filtro.
 *
 * @param tipo    tipo de evento.
 * @param usuario código del usuario.
 * @param desde   fecha mínima (inclusive).
 * @param hasta   fecha máxima (inclusive).
 */
public record FiltroLogs(TipoEvento tipo, String usuario, LocalDateTime desde, LocalDateTime hasta) {

    /**
     * @param e evento.
     * @return {@code true} si el evento cumple el filtro.
     */
    public boolean acepta(EventoLogDTO e) {
        return (tipo == null || tipo == e.tipo())
                && (usuario == null || usuario.equals(e.usuario()))
                && (desde == null || !e.fecha().isBefore(desde))
                && (hasta == null || !e.fecha().isAfter(hasta));
    }
}
