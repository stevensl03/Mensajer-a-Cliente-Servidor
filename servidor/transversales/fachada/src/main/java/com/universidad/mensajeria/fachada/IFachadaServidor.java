package com.universidad.mensajeria.fachada;

import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que las vistas del servidor pueden pedir (patrón Facade). Las vistas
 * dependen de esta interfaz, nunca de la implementación.
 */
public interface IFachadaServidor {

    /** Arranca la cola de mensajes y el transporte. */
    void iniciarServidor();

    /** Apagado ordenado: avisa a los clientes, cierra sesiones, cola y recursos. */
    void detenerServidor();

    /**
     * El ADMIN publica en el foro.
     *
     * @param c contenido.
     */
    void publicarEnForo(ContenidoDTO c);

    /**
     * El ADMIN envía un mensaje privado.
     *
     * @param destino código del destinatario.
     * @param c       contenido.
     */
    void enviarPrivado(String destino, ContenidoDTO c);

    /**
     * @param t       tipo de informe.
     * @param filtros filtros opcionales (tipoEvento, usuario, desde, hasta).
     * @return el informe en JSON.
     */
    String informe(TipoInforme t, Map<String, String> filtros);

    /** @return las sesiones activas. */
    List<SesionDTO> listarSesiones();

    /** @return todos los usuarios registrados con su estado de conexión. */
    List<EstadoUsuarioDTO> listarUsuarios();

    /**
     * Cierra una sesión por orden del administrador.
     *
     * @param idSesion identificador de la sesión.
     */
    void cerrarSesion(String idSesion);

    /**
     * @param o observador que se enterará de mensajes, eventos y sesiones.
     */
    void suscribir(IObservadorServidor o);

    /** @return la IP del servidor. */
    String getIpServidor();

    /**
     * Las vistas la usan para mostrar las miniaturas de un mensaje de imagen.
     *
     * @param idMensaje mensaje de imagen.
     * @param etapa     versión pedida (ORIGINAL o uno de los 5 filtros).
     * @return los bytes de esa versión, o vacío si no existe.
     */
    Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa);
}
