package com.universidad.mensajeria.soporte.almacenar.cache;

import com.universidad.mensajeria.utilerias.ISerializador;

import java.util.Objects;
import java.util.Properties;

/**
 * Crea las cachés (Factory). Si {@code cache.l2.habilitada=false} devuelve
 * solo L1.
 */
public final class FabricaCaches {

    private final Properties cfg;
    private final ISerializador json;

    /**
     * @param cfg  claves {@code cache.*} y {@code redis.*}.
     * @param json serializador para los valores que van a Redis.
     */
    public FabricaCaches(Properties cfg, ISerializador json) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.json = Objects.requireNonNull(json, "json");
    }

    /**
     * @param nombre prefijo de las claves.
     * @param tipo   clase de los valores.
     * @return la caché configurada.
     */
    public <K, V> ICache<K, V> crear(String nombre, Class<V> tipo) {
        throw new UnsupportedOperationException("Fase 3");
    }
}
