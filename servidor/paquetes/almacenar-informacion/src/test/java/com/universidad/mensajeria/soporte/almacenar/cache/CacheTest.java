package com.universidad.mensajeria.soporte.almacenar.cache;

import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTest {

    /** Caché en un mapa, para observar cada nivel. */
    private static final class CacheMapa<K, V> implements ICache<K, V> {
        final Map<K, V> datos = new HashMap<>();

        @Override
        public Optional<V> get(K k) {
            return Optional.ofNullable(datos.get(k));
        }

        @Override
        public void put(K k, V v) {
            datos.put(k, v);
        }

        @Override
        public void invalidar(K k) {
            datos.remove(k);
        }
    }

    @Test
    void l1Caffeine() {
        ICache<String, String> l1 = new CacheL1Caffeine<>(10, Duration.ofMinutes(1));
        l1.put("a", "1");
        assertEquals(Optional.of("1"), l1.get("a"));
        l1.invalidar("a");
        assertTrue(l1.get("a").isEmpty());
    }

    @Test
    void dosNivelesEscribeEnAmbosYPromueveDeL2AL1() {
        var l1 = new CacheMapa<String, String>();
        var l2 = new CacheMapa<String, String>();
        var cache = new CacheDosNiveles<>(l1, l2);

        cache.put("a", "1");
        assertEquals("1", l1.datos.get("a"));
        assertEquals("1", l2.datos.get("a"));

        l1.datos.clear();
        assertEquals(Optional.of("1"), cache.get("a"));
        assertEquals("1", l1.datos.get("a"), "el acierto en L2 se copia a L1");

        cache.invalidar("a");
        assertTrue(l1.datos.isEmpty() && l2.datos.isEmpty());
        assertTrue(cache.get("a").isEmpty());
    }

    @Test
    void sinL2LaFabricaDevuelveSoloL1() {
        var fabrica = new FabricaCaches(config(false, 6379), new SerializadorJSON());

        assertInstanceOf(CacheL1Caffeine.class, fabrica.crear("mensajes", String.class));
        fabrica.cerrar();
    }

    @Test
    void conRedisCaidoLaCacheSigueFuncionandoConL1() {
        // Puerto sin Redis: L2 falla y se comporta como fallo de caché.
        var fabrica = new FabricaCaches(config(true, 1), new SerializadorJSON());
        ICache<String, String> cache = fabrica.crear("prueba", String.class);

        assertInstanceOf(CacheDosNiveles.class, cache);
        cache.put("a", "1");
        assertEquals(Optional.of("1"), cache.get("a"));
        assertTrue(fabrica.crear("otra", String.class).get("a").isEmpty());
        fabrica.cerrar();
    }

    static Properties config(boolean l2, int puertoRedis) {
        var p = new Properties();
        p.setProperty("cache.l1.max", "100");
        p.setProperty("cache.l1.ttlMinutos", "10");
        p.setProperty("cache.l2.habilitada", String.valueOf(l2));
        p.setProperty("redis.host", System.getProperty("redis.host", "localhost"));
        p.setProperty("redis.puerto", String.valueOf(puertoRedis));
        p.setProperty("redis.ttlMinutos", "5");
        return p;
    }
}
