package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.persistencia.IDialectoSQL;

/**
 * Dialecto de PostgreSQL.
 */
public final class DialectoPostgreSQL implements IDialectoSQL {

    @Override
    public String carpetaMigraciones() {
        throw new UnsupportedOperationException("Fase 2");
    }

    @Override
    public String limitar(String sql, int n) {
        throw new UnsupportedOperationException("Fase 2");
    }
}
