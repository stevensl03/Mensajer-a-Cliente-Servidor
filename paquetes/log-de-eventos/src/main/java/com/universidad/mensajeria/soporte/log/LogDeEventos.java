package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.LogDTO;
import com.universidad.mensajeria.soporte.almacenar.AlmacenarInformacion;

import java.time.LocalDateTime;

/**
 * Registro de eventos y comunicaciones (paquete "LogDeEventos" del diagrama):
 * conexión, login fallido, mensaje, difusión, cierre y errores.
 *
 * <p>Construye el {@link LogDTO} enriquecido (usuario, IP, mensaje, conexión) y
 * delega la persistencia en {@link AlmacenarInformacion} (C-10).</p>
 */
public class LogDeEventos {

    private final AlmacenarInformacion almacenar;

    /**
     * @param almacenar intermediario de persistencia.
     */
    public LogDeEventos(AlmacenarInformacion almacenar) {
        this.almacenar = almacenar;
    }

    /**
     * Registra un evento genérico.
     *
     * @param tipo        tipo del evento.
     * @param nivel       INFO, WARN o ERROR.
     * @param descripcion detalle.
     * @param usuarioCodigo usuario asociado, o {@code null}.
     * @param ip          IP de origen, o {@code null}.
     * @param mensajeId   mensaje asociado, o {@code null}.
     * @param idConexion  conexión asociada, o {@code null}.
     */
    public void registrar(String tipo, String nivel, String descripcion,
                          String usuarioCodigo, String ip, String mensajeId, String idConexion) {
        // TODO: new LogDTO(0, tipo, nivel, descripcion, LocalDateTime.now(), ...) +
        //       almacenar.guardarLog(...).
    }

    /** Registra un login exitoso. */
    public void loginOk(String usuarioCodigo, String ip, String idConexion) {
        // TODO
    }

    /** Registra un login fallido. */
    public void loginFallido(String usuarioCodigo, String ip) {
        // TODO
    }

    /** Registra que se superó el cupo de conexiones. */
    public void cupoLleno(String usuarioCodigo, String ip) {
        // TODO
    }

    /** Registra el envío de un mensaje (privado o de foro). */
    public void mensaje(String tipo, String usuarioCodigo, String mensajeId, String idConexion) {
        // TODO
    }

    /** Registra una difusión. */
    public void difusion(String usuarioCodigo, String mensajeId) {
        // TODO
    }

    /** Registra un cierre forzado. */
    public void cierreForzado(String usuarioCodigo, String idConexion) {
        // TODO
    }

    /** Registra un error. */
    public void error(String descripcion, String usuarioCodigo, String idConexion) {
        // TODO
    }

    /** @return la marca de tiempo usada en los registros. */
    protected LocalDateTime ahora() {
        return LocalDateTime.now();
    }
}
