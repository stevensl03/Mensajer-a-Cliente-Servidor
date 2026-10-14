package com.universidad.mensajeria.fachada;

/**
 * Un cliente conectado, independiente del transporte. Lo implementa el
 * transporte (por ejemplo {@code CanalTCP}) y lo guarda ConexionesClientes.
 */
public interface ICanalCliente {

    /** @return identificador único del canal (también es el id de la sesión). */
    String getId();

    /** @return IP remota del cliente. */
    String getIpRemota();

    /**
     * Encola un JSON para enviarlo. No bloquea.
     *
     * @param json mensaje ya serializado.
     */
    void enviar(String json);

    /** Entrega lo pendiente y cierra el canal. Idempotente. */
    void cerrar();

    /** @return {@code true} si el canal sigue abierto. */
    boolean estaAbierto();
}
