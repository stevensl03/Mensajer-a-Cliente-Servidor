package com.universidad.mensajeria.fachada;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.InformeDTO;

import java.util.List;

/**
 * Contrato de la fachada (paquete transversal "Fachada" del diagrama).
 *
 * <p>La capa de presentación depende de esta interfaz, nunca de la
 * implementación, respetando la inversión de dependencias. Agrupa las
 * operaciones de administración del servidor que invocan las vistas; las
 * peticiones que llegan de los clientes por TCP las procesa el servidor a
 * través de {@code ManejadorTrama}.</p>
 */
public interface IFachadaDeServicios {

    /** Arranca el servidor (configuración, persistencia y socket). */
    void iniciar();

    /** Detiene el servidor y libera recursos. */
    void detener();

    /**
     * Registra un observador al que se notificarán los mensajes procesados.
     *
     * @param escucha observador de mensajes.
     */
    void registrarEscucha(EscuchaMensajes escucha);

    /**
     * Envía un texto a todos los usuarios conectados (RF-10).
     *
     * @param texto           contenido a difundir.
     * @param codigoRemitente código del usuario que difunde (por ejemplo el ADMIN).
     */
    void enviarDifusion(String texto, String codigoRemitente);

    /** @return los códigos de los usuarios conectados. */
    List<String> usuariosConectados();

    /** @return las conexiones activas. */
    List<SesionDTO> conexionesActivas();

    /**
     * Cierra la conexión indicada (RF-11).
     *
     * @param idConexion identificador de la conexión.
     */
    void cerrarConexion(String idConexion);

    /**
     * Cierra todas las conexiones activas de un usuario.
     *
     * @param codigoUsuario código del usuario.
     */
    void cerrarSesionesDe(String codigoUsuario);

    /**
     * Genera y persiste los informes solicitados (RF-13).
     *
     * @param generadoPor código del usuario que los solicita, o {@code null}.
     * @return los informes generados.
     */
    List<InformeDTO> generarInformes(String generadoPor);
}
