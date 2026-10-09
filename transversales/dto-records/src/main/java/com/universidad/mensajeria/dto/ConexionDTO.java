package com.universidad.mensajeria.dto;

import java.time.LocalDateTime;

/**
 * Conexión TCP de un usuario (permite multi-sesión).
 *
 * @param idConexion       UUID de la conexión.
 * @param codigoUsuario    usuario dueño de la conexión.
 * @param ipRemota         IP del cliente.
 * @param fechaConexion    apertura.
 * @param fechaDesconexion cierre, o {@code null} si sigue activa.
 * @param motivoCierre     NORMAL, FORZADO, ERROR o {@code null}.
 */
public record ConexionDTO(String idConexion, String codigoUsuario, String ipRemota,
                          LocalDateTime fechaConexion, LocalDateTime fechaDesconexion,
                          String motivoCierre) {
}
