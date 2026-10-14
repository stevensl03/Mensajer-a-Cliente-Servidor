package com.universidad.mensajeria.soporte.almacenar.cache;

import java.util.Optional;

/**
 * Caché clave-valor.
 *
 * @param <K> tipo de la clave.
 * @param <V> tipo del valor.
 */
public interface ICache<K, V> {

    Optional<V> get(K k);

    void put(K k, V v);

    void invalidar(K k);
}
