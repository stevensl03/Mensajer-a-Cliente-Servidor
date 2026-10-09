package com.universidad.mensajeria.dto;

/**
 * Comandos que un cliente puede enviar al servidor (contrato del protocolo).
 *
 * <p>Viaja por la red por nombre: no reordenar ni renombrar valores existentes.</p>
 */
public enum TipoComando {
    LOGIN,
    LOGOUT,
    LISTAR_USUARIOS,
    ENVIAR_FORO,
    ENVIAR_PRIVADO,
    HISTORIAL,
    INFORME
}
