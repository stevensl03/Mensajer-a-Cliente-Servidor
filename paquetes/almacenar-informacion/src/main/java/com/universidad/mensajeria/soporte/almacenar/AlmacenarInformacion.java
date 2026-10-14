package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.FabricaRepositorios;
import com.universidad.mensajeria.persistencia.IAlmacenamientoArchivos;
import com.universidad.mensajeria.soporte.almacenar.cache.ICache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link IAlmacenarInformacion} write-through: guarda archivos en
 * {@code ./almacen} y filas en la BD; usa la caché en las lecturas.
 */
public final class AlmacenarInformacion implements IAlmacenarInformacion {

    private final FabricaRepositorios repos;
    private final IAlmacenamientoArchivos archivos;
    private final ImportadorUsuarios importador;
    private final ICache<String, MensajeDTO> cacheMensajes;
    private final ICache<String, byte[]> cacheImagenes;

    public AlmacenarInformacion(FabricaRepositorios repos, IAlmacenamientoArchivos archivos,
                                ImportadorUsuarios importador, ICache<String, MensajeDTO> cacheMensajes,
                                ICache<String, byte[]> cacheImagenes) {
        this.repos = Objects.requireNonNull(repos, "repos");
        this.archivos = Objects.requireNonNull(archivos, "archivos");
        this.importador = Objects.requireNonNull(importador, "importador");
        this.cacheMensajes = Objects.requireNonNull(cacheMensajes, "cacheMensajes");
        this.cacheImagenes = Objects.requireNonNull(cacheImagenes, "cacheImagenes");
    }

    @Override
    public void inicializar() {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public List<UsuarioRegistroDTO> usuariosRegistrados() {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public MensajeDTO guardarMensaje(MensajeDTO m, List<ImagenProcesadaDTO> imgs) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public Optional<byte[]> leerImagen(String idMensaje, TipoFiltro etapa) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public void registrarConexion(SesionDTO s) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public void registrarDesconexion(String idSesion, LocalDateTime fin) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public void guardarLog(EventoLogDTO e) {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public List<MensajeDTO> historialMensajes() {
        throw new UnsupportedOperationException("Fase 3");
    }

    @Override
    public List<EventoLogDTO> historialLogs() {
        throw new UnsupportedOperationException("Fase 3");
    }
}
