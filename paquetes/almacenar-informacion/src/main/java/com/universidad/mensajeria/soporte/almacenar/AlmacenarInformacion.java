package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.ConexionDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.LogDTO;
import com.universidad.mensajeria.dto.MensajeImagenDTO;
import com.universidad.mensajeria.dto.MensajeTextoDTO;
import com.universidad.mensajeria.persistencia.IPersistencia;

/**
 * Intermediario que recibe lo que debe persistir y lo entrega a la capa de
 * persistencia (paquete "AlmacenarInformación" del diagrama).
 *
 * <p>Persistencia dual (C-12): primero deja la copia física en el directorio de
 * almacenamiento ({@code ./almacen}) y luego persiste en MySQL a través de
 * {@link IPersistencia}.</p>
 */
public class AlmacenarInformacion {

    private final IPersistencia persistencia;

    /**
     * @param persistencia capa de persistencia a la que se delega.
     */
    public AlmacenarInformacion(IPersistencia persistencia) {
        this.persistencia = persistencia;
    }

    /**
     * Persiste un mensaje de texto: copia en {@code ./almacen/texto/{id}.txt} y
     * alta en BD.
     *
     * @param mensaje mensaje de texto.
     */
    public void guardarMensajeTexto(MensajeTextoDTO mensaje) {
        // TODO: disco + persistencia.mensajes().guardarTexto(...).
    }

    /**
     * Persiste un mensaje de imagen: las 6 versiones en
     * {@code ./almacen/imagenes/{id}/} y el alta transaccional en BD.
     *
     * @param mensaje mensaje de imagen con sus 6 versiones.
     */
    public void guardarMensajeImagen(MensajeImagenDTO mensaje) {
        // TODO: disco + persistencia.mensajes().guardarImagen(...).
    }

    /**
     * Persiste un log.
     *
     * @param log evento a registrar.
     */
    public void guardarLog(LogDTO log) {
        // TODO
    }

    /**
     * Persiste una conexión nueva.
     *
     * @param conexion conexión.
     */
    public void guardarConexion(ConexionDTO conexion) {
        // TODO
    }

    /**
     * Actualiza una conexión (desconexión y motivo).
     *
     * @param conexion conexión.
     */
    public void actualizarConexion(ConexionDTO conexion) {
        // TODO
    }

    /**
     * Persiste un informe.
     *
     * @param informe informe a guardar.
     */
    public void guardarInforme(InformeDTO informe) {
        // TODO
    }
}
