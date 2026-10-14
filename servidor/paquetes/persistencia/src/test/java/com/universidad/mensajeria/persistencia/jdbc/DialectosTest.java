package com.universidad.mensajeria.persistencia.jdbc;

import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.persistencia.IDialectoSQL;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialectosTest {

    @Test
    void cadaDialectoTieneSuMigracionV1EnElClasspath() {
        for (IDialectoSQL d : List.of(new DialectoMySQL(), new DialectoPostgreSQL())) {
            assertNotNull(getClass().getClassLoader().getResource(d.carpetaMigraciones() + "/V1__esquema.sql"),
                    d.getClass().getSimpleName());
        }
        assertEquals("db/migration/mysql", new DialectoMySQL().carpetaMigraciones());
        assertEquals("db/migration/postgresql", new DialectoPostgreSQL().carpetaMigraciones());
    }

    @Test
    void limitar() {
        assertEquals("SELECT * FROM t LIMIT 5", new DialectoMySQL().limitar("SELECT * FROM t", 5));
        assertEquals("SELECT * FROM t LIMIT 5", new DialectoPostgreSQL().limitar("SELECT * FROM t", 5));
        assertThrows(IllegalArgumentException.class, () -> new DialectoMySQL().limitar("SELECT 1", 0));
    }

    @Test
    void claveDeConversacionMenorMayor() {
        LocalDateTime ahora = LocalDateTime.now();
        var privado = new MensajeDTO("m1", TipoMensaje.TEXTO, Canal.PRIVADO, "20201002", "20201001", ahora, "h",
                "t", 1, 1, null, null);
        var foro = new MensajeDTO("m2", TipoMensaje.TEXTO, Canal.FORO, "20201002", null, ahora, "h",
                "t", 1, 1, null, null);

        assertEquals("20201001|20201002", RepositorioMensajesJDBC.conversacion(privado));
        assertNull(RepositorioMensajesJDBC.conversacion(foro));
    }
}
