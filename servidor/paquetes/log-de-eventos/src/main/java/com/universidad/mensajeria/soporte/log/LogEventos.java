package com.universidad.mensajeria.soporte.log;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.soporte.almacenar.IAlmacenarInformacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public void conexion(SesionDTO s) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public void desconexion(SesionDTO s, String motivo) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public List<EventoLogDTO> consultar(FiltroLogs f) {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public List<EventoLogDTO> todos() {
        throw new UnsupportedOperationException("Fase 5");
    }

    @Override
    public void cargarHistorial() {
        throw new UnsupportedOperationException("Fase 5");
    }
}
