package com.universidad.mensajeria.soporte.almacenar;

import com.universidad.mensajeria.dto.EventoLogDTO;
import com.universidad.mensajeria.dto.ImagenProcesadaDTO;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.persistencia.FabricaRepositorios;
import com.universidad.mensajeria.persistencia.IAlmacenamientoArchivos;
import com.universidad.mensajeria.soporte.almacenar.cache.ICache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link IAlmacenarInformacion} write-through: guarda archivos en
 * {@code ./almacen} y filas en la BD; usa la caché en las lecturas.
 *
 * <p>Un mensaje se guarda así: primero los archivos y luego las filas en una
 * transacción. Si la transacción falla, se borran los archivos escritos.</p>
 */
public final class AlmacenarInformacion implements IAlmacenarInformacion {

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenarInformacion.class);

    static final String TEXTOS = "textos";
    static final String ORIGINALES = "originales";
    static final String FILTRADAS = "filtradas";

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
        if (repos.usuarios().contar() == 0) {
            List<UsuarioRegistroDTO> usuarios = importador.leer();
            repos.usuarios().guardarTodos(usuarios);
            LOG.info("Carga inicial: {} usuarios importados", usuarios.size());
        }
        int huerfanas = repos.conexiones().cerrarHuerfanas(LocalDateTime.now());
        if (huerfanas > 0) {
            LOG.info("Se cerraron {} conexiones que quedaron abiertas en la ejecución anterior", huerfanas);
        }
    }

    @Override
    public List<UsuarioRegistroDTO> usuariosRegistrados() {
        return repos.usuarios().listarTodos();
    }

    @Override
    public MensajeDTO guardarMensaje(MensajeDTO m, List<ImagenProcesadaDTO> imgs) {
        Objects.requireNonNull(m, "m");
        Objects.requireNonNull(imgs, "imgs");
        List<String> escritos = new ArrayList<>();
        try {
            MensajeDTO guardado = m.tipo() == TipoMensaje.TEXTO
                    ? escribirTexto(m, escritos)
                    : escribirImagenes(m, imgs, escritos);
            repos.mensajes().guardar(guardado);
            cacheMensajes.put(guardado.id(), guardado);
            return guardado;
        } catch (RuntimeException e) {
            borrar(escritos);
            throw e;
        }
    }

    @Override
    public Optional<byte[]> leerImagen(String idMensaje, TipoFiltro etapa) {
        String clave = idMensaje + ":" + etapa;
        Optional<byte[]> enCache = cacheImagenes.get(clave);
        if (enCache.isPresent()) {
            return enCache;
        }
        Optional<byte[]> datos = buscarMensaje(idMensaje)
                .flatMap(m -> m.imagenes().stream().filter(v -> v.etapa() == etapa).findFirst())
                .map(v -> archivos.leer(v.rutaArchivo()));
        datos.ifPresent(d -> cacheImagenes.put(clave, d));
        return datos;
    }

    @Override
    public void registrarConexion(SesionDTO s) {
        repos.conexiones().abrir(s);
    }

    @Override
    public void registrarDesconexion(String idSesion, LocalDateTime fin) {
        repos.conexiones().cerrar(idSesion, fin);
    }

    @Override
    public void guardarLog(EventoLogDTO e) {
        repos.logs().guardar(e);
    }

    @Override
    public List<MensajeDTO> historialMensajes() {
        List<MensajeDTO> mensajes = repos.mensajes().listarTodos();
        mensajes.forEach(m -> cacheMensajes.put(m.id(), m));
        return mensajes;
    }

    @Override
    public List<EventoLogDTO> historialLogs() {
        return repos.logs().listarTodos();
    }

    private MensajeDTO escribirTexto(MensajeDTO m, List<String> escritos) {
        Path ruta = archivos.guardar(TEXTOS, m.id() + ".txt", m.texto().getBytes(StandardCharsets.UTF_8));
        escritos.add(ruta.toString());
        return m;
    }

    private MensajeDTO escribirImagenes(MensajeDTO m, List<ImagenProcesadaDTO> imgs, List<String> escritos) {
        var versiones = new ArrayList<ImagenVersionDTO>();
        for (ImagenProcesadaDTO img : imgs) {
            TipoFiltro etapa = img.etapa();
            int orden = etapa.ordinal();
            byte[] datos = img.datos();
            Path ruta = etapa == TipoFiltro.ORIGINAL
                    ? archivos.guardar(ORIGINALES, m.id() + "_" + m.nombreArchivo(), datos)
                    : archivos.guardar(FILTRADAS, m.id() + "_" + orden + "_" + etapa + ".png", datos);
            escritos.add(ruta.toString());
            // La ORIGINAL conserva el nombre que envió el usuario: de ahí sale MensajeDTO.nombreArchivo al leer.
            String nombre = etapa == TipoFiltro.ORIGINAL ? m.nombreArchivo() : ruta.getFileName().toString();
            versiones.add(new ImagenVersionDTO(orden, etapa, nombre, ruta.toString(), datos.length, img.hash()));
        }
        return new MensajeDTO(m.id(), m.tipo(), m.canal(), m.remitente(), m.destinatario(), m.fecha(), m.hash(),
                m.texto(), m.numCaracteres(), m.numPalabras(), m.nombreArchivo(), versiones);
    }

    private Optional<MensajeDTO> buscarMensaje(String id) {
        Optional<MensajeDTO> enCache = cacheMensajes.get(id);
        if (enCache.isPresent()) {
            return enCache;
        }
        // Fallo de caché (expiró o es de otra ejecución): se recarga el historial una vez.
        return historialMensajes().stream().filter(m -> m.id().equals(id)).findFirst();
    }

    private void borrar(List<String> rutas) {
        for (String ruta : rutas) {
            try {
                archivos.borrar(ruta);
            } catch (RuntimeException e) {
                LOG.error("No se pudo borrar el archivo {} tras fallar el guardado", ruta, e);
            }
        }
    }
}
