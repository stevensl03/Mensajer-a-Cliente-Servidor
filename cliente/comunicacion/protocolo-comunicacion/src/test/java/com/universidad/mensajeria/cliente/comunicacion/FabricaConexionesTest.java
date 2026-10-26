package com.universidad.mensajeria.cliente.comunicacion;

import com.universidad.mensajeria.cliente.comunicacion.tcp.ConexionTCP;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FabricaConexionesTest {

    private final FabricaConexiones fabrica = new FabricaConexiones();

    @Test
    void tcpCreaUnaConexionTcpSinAbrir() {
        IConexionServidor c = fabrica.crear(TipoProtocolo.TCP, 1024);

        assertInstanceOf(ConexionTCP.class, c);
        assertFalse(c.estaConectada());
    }

    @Test
    void validaLosArgumentos() {
        assertThrows(NullPointerException.class, () -> fabrica.crear(null, 1024));
        assertThrows(IllegalArgumentException.class, () -> fabrica.crear(TipoProtocolo.TCP, 0));
    }
}
