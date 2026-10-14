package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.persistencia.IDialectoSQL;

/**
 * Dialecto de MySQL 8.
 */
public final class DialectoMySQL implements IDialectoSQL {

    @Override
    public String carpetaMigraciones() {
        return "db/migration/mysql";
    }

    @Override
    public String limitar(String sql, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n debe ser mayor que 0: " + n);
        }
        return sql + " LIMIT " + n;
    }
}
