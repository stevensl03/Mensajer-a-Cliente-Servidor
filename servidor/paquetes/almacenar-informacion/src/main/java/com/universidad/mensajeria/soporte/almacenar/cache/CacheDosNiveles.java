package com.universidad.mensajeria.soporte.almacenar.cache;

import java.util.Objects;
import java.util.Optional;

/**
 * Caché read-through de dos niveles (Decorator): L1 en la JVM y L2 en Redis.
 */
public final class CacheDosNiveles<K, V> implements ICache<K, V> {

    private final ICache<K, V> l1;
    private final ICache<K, V> l2;

    public CacheDosNiveles(ICache<K, V> l1, ICache<K, V> l2) {
        this.l1 = Objects.requireNonNull(l1, "l1");
        this.l2 = Objects.requireNonNull(l2, "l2");
    }

    @Override
    public Optional<V> get(K k) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public void put(K k, V v) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public void invalidar(K k) {
        throw new UnsupportedOperationException("Fase 3");
    }
}
