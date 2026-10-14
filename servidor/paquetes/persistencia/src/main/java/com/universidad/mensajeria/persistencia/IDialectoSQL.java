package com.universidad.mensajeria.persistencia;

/**
 * Diferencias de SQL entre motores (Strategy).
 */
public interface IDialectoSQL {

    /** @return carpeta de migraciones Flyway del motor ({@code db/migration/...}). */
    String carpetaMigraciones();

    /**
     * @param sql consulta sin límite.
     * @param n   máximo de filas.
     * @return la consulta con el límite en la sintaxis del motor.
     */
    String limitar(String sql, int n);
}
