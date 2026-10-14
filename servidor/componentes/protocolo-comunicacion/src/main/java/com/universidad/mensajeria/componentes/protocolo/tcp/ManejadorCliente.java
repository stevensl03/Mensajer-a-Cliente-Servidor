package com.universidad.mensajeria.componentes.protocolo.tcp;

import com.universidad.mensajeria.componentes.protocolo.IReceptorConexiones;
import com.universidad.mensajeria.componentes.protocolo.ProtocoloException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Hilo lector de una conexión TCP (uno por socket, en un hilo virtual). Lee
 * tramas en bucle y las entrega al receptor como JSON; al terminar llama a
 * {@code alDesconectar} una sola vez.
 *
 * <p>Una trama con longitud inválida cierra la conexión, porque ya no se
 * puede saber dónde empieza la siguiente.</p>
 */
final class ManejadorCliente implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorCliente.class);

    private final CanalTCP canal;
    private final IReceptorConexiones receptor;
    private final ServidorTCP servidor;

    ManejadorCliente(CanalTCP canal, IReceptorConexiones receptor, ServidorTCP servidor) {
        this.canal = canal;
        this.receptor = receptor;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try {
            receptor.alConectar(canal);
            while (canal.estaAbierto()) {
                String json = new String(canal.leerTrama(), StandardCharsets.UTF_8);
                try {
                    receptor.alRecibir(canal, json);
                } catch (RuntimeException e) {
                    LOG.error("Error atendiendo una trama de la conexión {}", canal.getId(), e);
                }
            }
        } catch (EOFException e) {
            LOG.debug("El cliente de la conexión {} cerró el flujo", canal.getId());
        } catch (ProtocoloException e) {
            LOG.warn("Trama inválida en la conexión {}: {}. Se cierra", canal.getId(), e.getMessage());
        } catch (IOException e) {
            LOG.debug("Conexión {} terminada: {}", canal.getId(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Error inesperado en la conexión {}", canal.getId(), e);
        } finally {
            canal.cerrar();
            try {
                receptor.alDesconectar(canal);
            } catch (RuntimeException e) {
                LOG.error("Error al desconectar la conexión {}", canal.getId(), e);
            }
            servidor.olvidar(canal);
        }
    }
}
