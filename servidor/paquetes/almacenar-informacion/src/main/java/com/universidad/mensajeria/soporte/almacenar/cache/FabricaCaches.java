package com.universidad.mensajeria.soporte.almacenar.cache;

import com.universidad.mensajeria.utilerias.ISerializador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Crea las cachés (Factory). Con {@code cache.l2.habilitada=false} devuelve
 * solo L1; con {@code true}, L1 + L2 sobre un único pool de Redis.
 */
public final class FabricaCaches {

    private static final Logger LOG = LoggerFactory.getLogger(FabricaCaches.class);

    private final ISerializador json;
    private final long l1Max;
    private final Duration l1Ttl;
    private final Duration l2Ttl;
    private final JedisPool redis;

    /**
     * @param cfg  claves {@code cache.*} y {@code redis.*}.
     * @param json serializador para los valores que van a Redis.
     */
    public FabricaCaches(Properties cfg, ISerializador json) {
        Objects.requireNonNull(cfg, "cfg");
        this.json = Objects.requireNonNull(json, "json");
        this.l1Max = Long.parseLong(requerida(cfg, "cache.l1.max"));
        this.l1Ttl = Duration.ofMinutes(Long.parseLong(requerida(cfg, "cache.l1.ttlMinutos")));
        if (Boolean.parseBoolean(requerida(cfg, "cache.l2.habilitada"))) {
            String host = requerida(cfg, "redis.host");
            int puerto = Integer.parseInt(requerida(cfg, "redis.puerto"));
            this.l2Ttl = Duration.ofMinutes(Long.parseLong(requerida(cfg, "redis.ttlMinutos")));
            this.redis = new JedisPool(host, puerto);
            LOG.info("Caché de dos niveles: L1 Caffeine + L2 Redis en {}:{}", host, puerto);
        } else {
            this.l2Ttl = null;
            this.redis = null;
            LOG.info("Caché de un nivel: L1 Caffeine (L2 deshabilitada)");
        }
    }

    /**
     * @param nombre prefijo de las claves en Redis.
     * @param tipo   clase de los valores.
     * @return la caché configurada.
     */
    public <K, V> ICache<K, V> crear(String nombre, Class<V> tipo) {
        ICache<K, V> l1 = new CacheL1Caffeine<>(l1Max, l1Ttl);
        if (redis == null) {
            return l1;
        }
        return new CacheDosNiveles<>(l1, new CacheL2Redis<>(redis, nombre, l2Ttl, json, tipo));
    }

    /** Cierra el pool de Redis (apagado ordenado). Idempotente. */
    public void cerrar() {
        if (redis != null) {
            redis.close();
        }
    }

    private static String requerida(Properties p, String clave) {
        String valor = p.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la clave de configuración '" + clave + "'");
        }
        return valor.strip();
    }
}
