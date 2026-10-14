package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.componentes.protocolo.tcp.ServidorTCP;
import com.universidad.mensajeria.utilerias.ConfiguracionServidor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricaProtocolosTest {

    private static final Path CONFIG = Path.of("../../config");

    @Test
    void creaElTransporteConfiguradoSinIniciarlo() {
        var cfg = ConfiguracionServidor.cargar(CONFIG.resolve("servidor.properties"), CONFIG.resolve("bd.properties"));

        IProtocoloComunicacion protocolo = FabricaProtocolos.crear(TipoProtocolo.valueOf(cfg.protocolo()), cfg);

        assertInstanceOf(ServidorTCP.class, protocolo);
        assertFalse(protocolo.estaActivo());
    }

    @Test
    void protocoloDesconocido() {
        assertThrows(IllegalArgumentException.class, () -> TipoProtocolo.valueOf("UDP"));
        assertThrows(NullPointerException.class, () -> FabricaProtocolos.crear(null, null));
    }
}
