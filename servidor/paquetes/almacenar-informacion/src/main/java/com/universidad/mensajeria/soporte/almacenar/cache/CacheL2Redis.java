package com.universidad.mensajeria.soporte.almacenar.cache;

import com.universidad.mensajeria.utilerias.ISerializador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Caché L2 en Redis, compartida entre reinicios. Los valores se guardan como
 * JSON bajo la clave {@code prefijo:clave}.
 *
 * <p>La caché nunca rompe la aplicación: si Redis no responde, se registra
 * un aviso y se comporta como un fallo de caché.</p>
 */
public final class CacheL2Redis<K, V> implements ICache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(CacheL2Redis.class);

    private final JedisPool pool;
    private final String prefijo;
    private final Duration ttl;
    private final ISerializador json;
    private final Class<V> tipo;

    /**
     * @param pool    pool compartido (lo crea y lo cierra {@link FabricaCaches}).
     * @param prefijo prefijo de las claves (nombre de la caché).
     * @param ttl     tiempo de vida de cada entrada.
     * @param json    serializador de los valores.
     * @param tipo    clase de los valores.
     */
    public CacheL2Redis(JedisPool pool, String prefijo, Duration ttl, ISerializador json, Class<V> tipo) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.prefijo = Objects.requireNonNull(prefijo, "prefijo");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.json = Objects.requireNonNull(json, "json");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
    }

    @Override
    public Optional<V> get(K k) {
        try (Jedis redis = pool.getResource()) {
            return Optional.ofNullable(redis.get(clave(k))).map(s -> json.desdeJson(s, tipo));
        } catch (JedisException | IllegalArgumentException e) {
            LOG.warn("Caché L2 '{}' no disponible al leer: {}", prefijo, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(K k, V v) {
        try (Jedis redis = pool.getResource()) {
            redis.setex(clave(k), ttl.toSeconds(), json.aJson(v));
        } catch (JedisException | IllegalArgumentException e) {
            LOG.warn("Caché L2 '{}' no disponible al escribir: {}", prefijo, e.getMessage());
        }
    }

    @Override
    public void invalidar(K k) {
        try (Jedis redis = pool.getResource()) {
            redis.del(clave(k));
        } catch (JedisException e) {
            LOG.warn("Caché L2 '{}' no disponible al invalidar: {}", prefijo, e.getMessage());
        }
    }

    private String clave(K k) {
        return prefijo + ":" + Objects.requireNonNull(k, "k");
    }
}
