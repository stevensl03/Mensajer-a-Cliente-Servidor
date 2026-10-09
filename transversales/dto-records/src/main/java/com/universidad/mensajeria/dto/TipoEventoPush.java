package com.universidad.mensajeria.dto;

/**
 * Eventos que el servidor envía sin que el cliente los pida (contrato del
 * protocolo).
 *
 * <p>Viaja por la red por nombre: no reordenar ni renombrar valores existentes.</p>
 */
public enum TipoEventoPush {
    INFO_SERVIDOR,
    MENSAJE_NUEVO,
    USUARIO_CONECTADO,
    USUARIO_DESCONECTADO,
    SESION_CERRADA,
    CUPO_LLENO
}
