package com.universidad.mensajeria.persistencia;

/**
 * Crea los repositorios (Abstract Factory).
 */
public interface FabricaRepositorios {

    IRepositorioUsuarios usuarios();

    IRepositorioConexiones conexiones();

    IRepositorioMensajes mensajes();

    IRepositorioLogs logs();

    IAlmacenamientoArchivos archivos();
}
