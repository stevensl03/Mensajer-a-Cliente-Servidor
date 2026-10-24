package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link ILogEventos}: escribe en SLF4J (consola y archivo), indexa en memoria
 * y persiste en la BD.
 */
public final class LogEventos implements ILogEventos {

    private static final Logger LOG = LoggerFactory.getLogger(LogEventos.class);

    private final ConcurrentLinkedDeque<EventoLogDTO> eventos = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<TipoEvento, Queue<EventoLogDTO>> porTipo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<EventoLogDTO>> porUsuario = new ConcurrentHashMap<>();
    private final IAlmacenarInformacion almacenamiento;

    public LogEventos(IAlmacenarInformacion almacenamiento) {
        this.almacenamiento = Objects.requireNonNull(almacenamiento, "almacenamiento");
    }

    @Override
    public void registrar(TipoEvento t, String descripcion, String usuario, String ip) {
        var e = new EventoLogDTO(UUID.randomUUID().toString(), t, descripcion, usuario, ip, LocalDateTime.now());
        escribir(e);
        try {
            almacenamiento.guardarLog(e);
        } catch (RuntimeException ex) {
            // Un fallo de la BD no debe tumbar el caso de uso que registra el evento.
            LOG.warn("No se pudo persistir el evento {} ({})", e.id(), t, ex);
        }
        indexar(e);
    }

    @Override
    public void conexion(SesionDTO s) {
        try {
            almacenamiento.registrarConexion(s);
        } catch (RuntimeException ex) {
            // conexiones_usuario es solo historial: un fallo de la BD no debe dejar el LOGIN a medias.
            LOG.warn("No se pudo persistir la conexión {}", s.idSesion(), ex);
        }
        registrar(TipoEvento.LOGIN_OK, "LOGIN_OK sesion=" + s.idSesion(), s.codigo(), s.ip());
    }

    @Override
    public void desconexion(SesionDTO s, String motivo) {
        try {
            almacenamiento.registrarDesconexion(s.idSesion(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            // Si no se registra aquí, cerrarHuerfanas la cierra en el próximo arranque.
            LOG.warn("No se pudo persistir la desconexión {}", s.idSesion(), ex);
        }
        registrar(TipoEvento.DESCONEXION, "DESCONEXION sesion=" + s.idSesion() + " motivo=" + motivo,
                s.codigo(), s.ip());
    }

    @Override
    public List<EventoLogDTO> consultar(FiltroLogs f) {
        Collection<EventoLogDTO> base;
        if (f.tipo() != null) {
            base = porTipo.getOrDefault(f.tipo(), new ConcurrentLinkedQueue<>());
        } else if (f.usuario() != null) {
            base = porUsuario.getOrDefault(f.usuario(), new ConcurrentLinkedQueue<>());
        } else {
            base = eventos;
        }
        return base.stream().filter(f::acepta).toList();
    }

    @Override
    public List<EventoLogDTO> todos() {
        return List.copyOf(eventos);
    }

    @Override
    public void cargarHistorial() {
        almacenamiento.historialLogs().stream()
                .sorted(Comparator.comparing(EventoLogDTO::fecha))
                .forEach(this::indexar);
    }

    private void indexar(EventoLogDTO e) {
        eventos.addLast(e);
        porTipo.computeIfAbsent(e.tipo(), k -> new ConcurrentLinkedQueue<>()).add(e);
        if (e.usuario() != null) {
            porUsuario.computeIfAbsent(e.usuario(), k -> new ConcurrentLinkedQueue<>()).add(e);
        }
    }

    /** Escribe la línea en SLF4J con {@code usuario} e {@code ip} en el MDC (patrón de logback.xml). */
    private static void escribir(EventoLogDTO e) {
        MDC.put("usuario", e.usuario() == null ? "" : e.usuario());
        MDC.put("ip", e.ip() == null ? "" : e.ip());
        try {
            switch (e.tipo()) {
                case ERROR -> LOG.error(e.descripcion());
                case LOGIN_FALLIDO, CUPO_LLENO -> LOG.warn(e.descripcion());
                default -> LOG.info(e.descripcion());
            }
        } finally {
            MDC.remove("usuario");
            MDC.remove("ip");
        }
    }
}
