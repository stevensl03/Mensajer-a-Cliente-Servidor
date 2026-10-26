package com.universidad.mensajeria.cliente.fachada;

import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Lo único que ven los ViewModels. Los métodos que bloquean o leen H2
 * ({@link #conectar}, {@link #logout}, {@link #historial},
 * {@link #mensajesGuardados}) no se llaman desde el hilo de la UI.
 *
 * <p>Los rechazos del servidor llegan como
 * {@link com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor} con
 * su {@code CodigoError} (D-16).</p>
 */
public interface IFachadaCliente {

    /**
     * Conecta si aún no hay conexión con ese servidor (D-06): tras un login
     * fallido por credenciales se reutiliza la misma.
     *
     * @return la IP del servidor ({@code INFO_SERVIDOR}).
     * @throws java.io.UncheckedIOException si no se puede conectar.
     */
    String conectar(String host, int puerto);

    /** @return el usuario autenticado; antes hay que {@link #conectar}. */
    CompletableFuture<UsuarioDTO> login(String codigo, String clave);

    /**
     * Cierra la sesión pedida por el usuario (sin aviso {@code onDesconexion}) y
     * la conexión; sin sesión, cierra solo la conexión. Idempotente.
     */
    void logout();

    CompletableFuture<List<EstadoUsuarioDTO>> usuarios();

    /** @return se completa con ENCOLADO; el mensaje llega después por {@code onMensaje}. */
    CompletableFuture<Void> enviarForo(ContenidoDTO c);

    /** @return se completa con ENCOLADO; el mensaje llega después por {@code onMensaje}. */
    CompletableFuture<Void> enviarPrivado(String destino, ContenidoDTO c);

    /**
     * @param conversacion {@code FORO} o el código del otro usuario.
     * @return lo guardado localmente; lo que falte llega del servidor por {@code onMensaje} (D-11).
     */
    List<MensajeDTO> historial(String conversacion);

    CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa);

    /** @return el informe y su JSON tal como llegó (D-08). */
    CompletableFuture<InformeRecibido> informe(TipoInforme t, Map<String, String> filtros);

    /** @return la IP del servidor, o {@code null} si nunca se conectó. */
    String getIpServidor();

    /** @return el puerto del servidor conectado, o 0 si nunca se conectó (D-19, barra de estado de C2). */
    int getPuerto();

    void suscribir(IObservadorCliente o);

    /** @return el usuario de la sesión (D-05). */
    Optional<UsuarioDTO> usuarioActual();

    /** @return el id de la sesión local, o {@code -1} (D-05). */
    long sesionLocal();

    /** @return los mensajes guardados en H2 (D-12). */
    long mensajesGuardados();
}
