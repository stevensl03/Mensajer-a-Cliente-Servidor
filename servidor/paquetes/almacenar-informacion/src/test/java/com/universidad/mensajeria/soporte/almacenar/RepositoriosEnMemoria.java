package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.AlmacenamientoDirectorioLocal;
import com.universidad.mensajeria.persistencia.FabricaRepositorios;
import com.universidad.mensajeria.persistencia.IAlmacenamientoArchivos;
import com.universidad.mensajeria.persistencia.IRepositorioConexiones;
import com.universidad.mensajeria.persistencia.IRepositorioLogs;
import com.universidad.mensajeria.persistencia.IRepositorioMensajes;
import com.universidad.mensajeria.persistencia.IRepositorioUsuarios;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositorios falsos en memoria, solo para las pruebas de este módulo.
 * {@link #fallarAlGuardarMensajes} simula que la transacción falla.
 */
final class RepositoriosEnMemoria implements FabricaRepositorios {

    final List<UsuarioRegistroDTO> usuarios = new ArrayList<>();
    final Map<String, LocalDateTime> conexiones = new LinkedHashMap<>();
    final List<MensajeDTO> mensajes = new ArrayList<>();
    final List<EventoLogDTO> logs = new ArrayList<>();
    boolean fallarAlGuardarMensajes;
    int lecturasDeMensajes;

    private final IAlmacenamientoArchivos archivos;

    RepositoriosEnMemoria(Path raiz) {
        this.archivos = new AlmacenamientoDirectorioLocal(raiz);
    }

    @Override
    public IRepositorioUsuarios usuarios() {
        return new IRepositorioUsuarios() {
            @Override
            public List<UsuarioRegistroDTO> listarTodos() {
                return List.copyOf(usuarios);
            }

            @Override
            public int contar() {
                return usuarios.size();
            }

            @Override
            public void guardarTodos(List<UsuarioRegistroDTO> l) {
                usuarios.addAll(l);
            }
        };
    }

    @Override
    public IRepositorioConexiones conexiones() {
        return new IRepositorioConexiones() {
            @Override
            public void abrir(SesionDTO s) {
                conexiones.put(s.idSesion(), null);
            }

            @Override
            public void cerrar(String idSesion, LocalDateTime fin) {
                conexiones.put(idSesion, fin);
            }

            @Override
            public int cerrarHuerfanas(LocalDateTime fin) {
                int n = 0;
                for (var e : conexiones.entrySet()) {
                    if (e.getValue() == null) {
                        e.setValue(fin);
                        n++;
                    }
                }
                return n;
            }
        };
    }

    @Override
    public IRepositorioMensajes mensajes() {
        return new IRepositorioMensajes() {
            @Override
            public void guardar(MensajeDTO m) {
                if (fallarAlGuardarMensajes) {
                    throw new IllegalStateException("transacción fallida (simulada)");
                }
                mensajes.add(m);
            }

            @Override
            public List<MensajeDTO> listarTodos() {
                lecturasDeMensajes++;
                return List.copyOf(mensajes);
            }
        };
    }

    @Override
    public IRepositorioLogs logs() {
        return new IRepositorioLogs() {
            @Override
            public void guardar(EventoLogDTO e) {
                logs.add(e);
            }

            @Override
            public List<EventoLogDTO> listarTodos() {
                return List.copyOf(logs);
            }
        };
    }

    @Override
    public IAlmacenamientoArchivos archivos() {
        return archivos;
    }
}
