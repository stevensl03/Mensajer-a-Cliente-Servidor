package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.soporte.log.FiltroLogs;
import com.universidad.mensajeria.soporte.log.ILogEventos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** ILogEventos en memoria que graba los eventos, como lo haría LogEventos sin BD. */
final class LogGrabador implements ILogEventos {

    final List<EventoLogDTO> eventos = new CopyOnWriteArrayList<>();
    final List<SesionDTO> conexiones = new CopyOnWriteArrayList<>();
    final List<String> desconexiones = new CopyOnWriteArrayList<>();

    @Override
    public void registrar(TipoEvento t, String descripcion, String usuario, String ip) {
        eventos.add(new EventoLogDTO(UUID.randomUUID().toString(), t, descripcion, usuario, ip, LocalDateTime.now()));
    }

    @Override
    public void conexion(SesionDTO s) {
        conexiones.add(s);
        registrar(TipoEvento.LOGIN_OK, "LOGIN_OK sesion=" + s.idSesion(), s.codigo(), s.ip());
    }

    @Override
    public void desconexion(SesionDTO s, String motivo) {
        desconexiones.add(s.idSesion() + ":" + motivo);
        registrar(TipoEvento.DESCONEXION, "DESCONEXION sesion=" + s.idSesion() + " motivo=" + motivo,
                s.codigo(), s.ip());
    }

    @Override
    public List<EventoLogDTO> consultar(FiltroLogs f) {
        return eventos.stream().filter(f::acepta).toList();
    }

    @Override
    public List<EventoLogDTO> todos() {
        return List.copyOf(eventos);
    }

    @Override
    public void cargarHistorial() {
        // Sin historial persistido en las pruebas.
    }

    List<TipoEvento> tipos() {
        return eventos.stream().map(EventoLogDTO::tipo).toList();
    }
}
