package com.universidad.mensajeria.soporte.almacenar.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Caché L1 dentro de la JVM (Caffeine), acotada por tamaño y con expiración.
 */
public final class CacheL1Caffeine<K, V> implements ICache<K, V> {

    private final Cache<K, V> cache;

    /**
     * @param max máximo de entradas ({@code cache.l1.max}).
     * @param ttl tiempo de vida desde la escritura ({@code cache.l1.ttlMinutos}).
     */
    public CacheL1Caffeine(long max, Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(max)
                .expireAfterWrite(Objects.requireNonNull(ttl, "ttl"))
                .build();
    }

    @Override
    public Optional<V> get(K k) {
        return Optional.ofNullable(cache.getIfPresent(k));
    }

    @Override
    public void put(K k, V v) {
        cache.put(k, v);
    }

    @Override
    public void invalidar(K k) {
        cache.invalidate(k);
    }
}
