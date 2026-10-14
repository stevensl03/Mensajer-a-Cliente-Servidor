package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Única puerta hacia la persistencia: archivos en {@code ./almacen}, filas en
 * la BD y caché.
 */
public interface IAlmacenarInformacion {

    /** Importa los usuarios si la BD está vacía y cierra las conexiones huérfanas. */
    void inicializar();

    /** @return los usuarios registrados (con el hash de la contraseña). */
    List<UsuarioRegistroDTO> usuariosRegistrados();

    /**
     * Escribe los archivos y luego las filas en una transacción; si la
     * transacción falla, borra los archivos.
     *
     * @param m    mensaje sin rutas.
     * @param imgs versiones de la imagen (vacía si es texto).
     * @return el mensaje con las rutas de sus archivos.
     */
    MensajeDTO guardarMensaje(MensajeDTO m, List<ImagenProcesadaDTO> imgs);

    /**
     * @param idMensaje mensaje de imagen.
     * @param etapa     versión pedida.
     * @return los bytes de la versión, si existe.
     */
    Optional<byte[]> leerImagen(String idMensaje, TipoFiltro etapa);

    /** @param s sesión abierta. */
    void registrarConexion(SesionDTO s);

    /**
     * @param idSesion sesión cerrada.
     * @param fin      momento del cierre.
     */
    void registrarDesconexion(String idSesion, LocalDateTime fin);

    /** @param e evento a guardar. */
    void guardarLog(EventoLogDTO e);

    /** @return todos los mensajes guardados. */
    List<MensajeDTO> historialMensajes();

    /** @return todos los eventos guardados. */
    List<EventoLogDTO> historialLogs();
}
