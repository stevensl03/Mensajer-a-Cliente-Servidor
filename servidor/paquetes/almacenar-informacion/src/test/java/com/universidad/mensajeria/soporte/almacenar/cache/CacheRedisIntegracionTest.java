package com.universidad.mensajeria.soporte.almacenar.cache;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * L2 contra un Redis real. Se ejecuta con
 * {@code mvn verify -Pintegracion -pl paquetes/almacenar-informacion -am -Dredis.puerto=6379}.
 */
@Tag("integracion")
class CacheRedisIntegracionTest {

    @Test
    void l2CompartidaEntreFabricasComoEntreReinicios() {
        String puerto = System.getProperty("redis.puerto");
        assumeTrue(puerto != null, "Defina -Dredis.puerto para ejecutar la prueba con Redis");
        var json = new SerializadorJSON();
        String id = UUID.randomUUID().toString();
        var mensaje = new MensajeDTO(id, TipoMensaje.TEXTO, Canal.FORO, "20201001", null,
                LocalDateTime.of(2026, 10, 14, 9, 30, 0, 123_000_000), "h", "Hola", 4, 1, null, List.of());

        var antes = new FabricaCaches(CacheTest.config(true, Integer.parseInt(puerto)), json);
        antes.<String, MensajeDTO>crear("mensajes", MensajeDTO.class).put(id, mensaje);
        antes.<String, byte[]>crear("imagenes", byte[].class).put(id, new byte[]{1, 2, 3});
        antes.cerrar();

        var despues = new FabricaCaches(CacheTest.config(true, Integer.parseInt(puerto)), json);
        ICache<String, MensajeDTO> mensajes = despues.crear("mensajes", MensajeDTO.class);
        ICache<String, byte[]> imagenes = despues.crear("imagenes", byte[].class);

        assertEquals(Optional.of(mensaje), mensajes.get(id));
        assertArrayEquals(new byte[]{1, 2, 3}, imagenes.get(id).orElseThrow());
        mensajes.invalidar(id);
        assertTrue(mensajes.get(id).isEmpty());
        despues.cerrar();
    }
}
