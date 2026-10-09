package com.universidad.mensajeria.componentes.protocolo;

import com.universidad.mensajeria.dto.PeticionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;

/**
 * Hilo lector de una conexión TCP (uno por socket, en un hilo virtual).
 *
 * <p>Un JSON inválido se responde con error y la conexión sigue; una trama
 * con longitud inválida cierra la conexión, porque ya no se puede saber dónde
 * empieza la siguiente.</p>
 */
final class ManejadorCliente implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorCliente.class);

    private final CanalTCP canal;
    private final ServidorTCP servidor;

    ManejadorCliente(CanalTCP canal, ServidorTCP servidor) {
        this.canal = canal;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        boolean registrado = false;
        try {
            registrado = servidor.alConectar(canal);
            if (!registrado) {
                return;
            }
            while (true) {
                byte[] trama = canal.leerTrama();
                PeticionDTO peticion;
                try {
                    peticion = canal.decodificar(trama, PeticionDTO.class);
                } catch (ProtocoloException e) {
                    servidor.atenderInvalida(canal, e.getMessage());
                    continue;
                }
                servidor.atender(peticion, canal);
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
            if (registrado) {
                servidor.alDesconectar(canal);
            } else {
                canal.cerrar();
            }
            servidor.olvidar(canal);
        }
    }
}
