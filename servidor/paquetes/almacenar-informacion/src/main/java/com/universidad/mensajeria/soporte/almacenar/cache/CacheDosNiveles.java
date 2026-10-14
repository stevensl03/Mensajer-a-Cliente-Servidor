package com.universidad.mensajeria.soporte.almacenar.cache;

import java.util.Objects;
import java.util.Optional;

/**
 * Caché read-through de dos niveles (Decorator): L1 en la JVM y L2 en Redis.
 * Un acierto en L2 se promueve a L1.
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
        Optional<V> enL1 = l1.get(k);
        if (enL1.isPresent()) {
            return enL1;
        }
        Optional<V> enL2 = l2.get(k);
        enL2.ifPresent(v -> l1.put(k, v));
        return enL2;
    }

    @Override
    public void put(K k, V v) {
        l1.put(k, v);
        l2.put(k, v);
    }

    @Override
    public void invalidar(K k) {
        l1.invalidar(k);
        l2.invalidar(k);
    }
}
