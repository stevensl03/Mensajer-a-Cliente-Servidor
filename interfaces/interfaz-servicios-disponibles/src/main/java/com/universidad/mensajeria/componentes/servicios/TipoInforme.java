package com.universidad.mensajeria.componentes.servicios;

/**
 * Tipos de informe que el servidor puede generar (columna
 * {@code informes.tipo}).
 */
public enum TipoInforme {

    /** Usuarios registrados. */
    USUARIOS_REGISTRADOS,

    /** Usuarios conectados con la cantidad de veces que se han conectado. */
    USUARIOS_CONECTADOS,

    /** Listado de mensajes con detalle. */
    MENSAJES,

    /** Logs generados. */
    LOGS
}
