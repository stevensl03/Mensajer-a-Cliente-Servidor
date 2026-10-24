package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoEventoPush;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.ILogEventos;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Login (autenticación + cupo global) y cierre de sesiones. Registra los
 * eventos y avisa a todos los clientes y a las vistas (§6.2 y §6.6).
 */
public final class ControlSesiones {

    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final ILogEventos log;
    private final EnrutadorMensajes enrutador;
    private final NotificadorObservadores notificador;

    public ControlSesiones(IUsuariosDisponibles usuarios, IGestorConexiones conexiones, ILogEventos log,
                           EnrutadorMensajes enrutador, NotificadorObservadores notificador) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.log = Objects.requireNonNull(log, "log");
        this.enrutador = Objects.requireNonNull(enrutador, "enrutador");
        this.notificador = Objects.requireNonNull(notificador, "notificador");
    }

    /**
     * @param c      canal que pide el login.
     * @param codigo código del usuario.
     * @param clave  contraseña en claro (nunca se registra).
     * @return el usuario autenticado.
     * @throws ExcepcionNegocio CREDENCIALES_INVALIDAS, PETICION_INVALIDA (el canal ya tiene sesión)
     *                          o CUPO_LLENO.
     */
    public UsuarioDTO abrir(ICanalCliente c, String codigo, String clave) {
        Optional<UsuarioDTO> autenticado = usuarios.autenticar(codigo, clave);
        if (autenticado.isEmpty()) {
            log.registrar(TipoEvento.LOGIN_FALLIDO, "LOGIN_FALLIDO codigo=" + codigo, codigo, c.getIpRemota());
            throw new ExcepcionNegocio(CodigoError.CREDENCIALES_INVALIDAS, "Código o contraseña incorrectos");
        }
        UsuarioDTO u = autenticado.get();
        if (conexiones.usuarioDe(c.getId()).isPresent()) {
            throw new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "La conexión ya tiene una sesión iniciada");
        }
        if (!conexiones.abrirSesion(c, u)) {
            log.registrar(TipoEvento.CUPO_LLENO, "CUPO_LLENO codigo=" + u.codigo(), u.codigo(), c.getIpRemota());
            throw new ExcepcionNegocio(CodigoError.CUPO_LLENO, "No hay cupos libres en el servidor");
        }
        SesionDTO sesion = sesion(c.getId());
        log.conexion(sesion);
        enrutador.difundir(enrutador.evento(TipoEventoPush.USUARIO_CONECTADO, Map.of(
                "codigo", u.codigo(),
                "nombreCompleto", sesion.nombreCompleto(),
                "sesiones", conexiones.canalesDe(u.codigo()).size())));
        notificador.sesiones(conexiones.resumen());
        return u;
    }

    /**
     * Cierra la sesión una sola vez: si ya estaba cerrada, no hace nada.
     *
     * @param idSesion sesión a cerrar.
     * @param motivo   LOGOUT, DESCONEXION, CERRADA_POR_ADMIN o SERVIDOR_DETENIDO.
     */
    public void cerrar(String idSesion, String motivo) {
        Optional<SesionDTO> cerrada = conexiones.cerrarSesion(idSesion);
        if (cerrada.isEmpty()) {
            return;
        }
        SesionDTO s = cerrada.get();
        log.desconexion(s, motivo);
        enrutador.difundir(enrutador.evento(TipoEventoPush.USUARIO_DESCONECTADO, Map.of(
                "codigo", s.codigo(),
                "sesiones", conexiones.canalesDe(s.codigo()).size())));
        notificador.sesiones(conexiones.resumen());
    }

    private SesionDTO sesion(String idSesion) {
        return conexiones.resumen().stream()
                .filter(s -> s.idSesion().equals(idSesion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La sesión recién abierta no aparece: " + idSesion));
    }
}
