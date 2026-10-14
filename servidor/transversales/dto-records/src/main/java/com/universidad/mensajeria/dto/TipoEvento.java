package com.universidad.mensajeria.dto;

/**
 * Tipos de evento del log del servidor (tabla {@code logs_eventos}).
 */
public enum TipoEvento {
    CONEXION,
    DESCONEXION,
    LOGIN_OK,
    LOGIN_FALLIDO,
    CUPO_LLENO,
    MENSAJE_FORO,
    MENSAJE_PRIVADO,
    ERROR
}
