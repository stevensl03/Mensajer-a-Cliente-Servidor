package com.universidad.mensajeria.cliente.servicios.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.universidad.mensajeria.cliente.persistencia.IRepositorioMensajesLocal;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.utilerias.ExcepcionServidor;
import com.universidad.mensajeria.cliente.utilerias.ISerializador;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Componente Chat (cliente §5.3). Todo lo enviado y recibido en la sesión se
 * guarda en H2; las imágenes descargadas también.
 *
 * <p>Los bytes originales de una imagen enviada se guardan como
 * {@code ADJUNTO ORIGINAL} cuando su mensaje ya está en H2. La respuesta
 * {@code ENCOLADO} y el push {@code MENSAJE_NUEVO} pueden llegar en cualquier
 * orden (R-48): los dos caminos intentan guardarlo y el adjunto es idempotente.</p>
 */
public final class ServicioChat implements IServicioChat {

    /** Límites por defecto del servidor, que no viajan por el protocolo (R-50). */
    static final int MAX_TEXTO_CARACTERES = 4000;
    static final int MAX_IMAGEN_BYTES = 5_242_880;
    static final Set<String> EXTENSIONES = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    /** Mensajes que se retienen antes de abrir la base (R-30). */
    static final int MAX_BUFER = 500;

    private static final TypeReference<List<MensajeDTO>> MENSAJES = new TypeReference<>() { };

    private final IProtocoloCliente protocolo;
    private final IRepositorioMensajesLocal repo;
    private final CacheMensajesSesion cache;
    private final ISerializador json;
    private final ReentrantLock sesion = new ReentrantLock();
    private final Deque<MensajeDTO> bufer = new ArrayDeque<>();
    /** idMensaje → original enviado, hasta que su mensaje esté en H2. */
    private final Map<String, Original> originalesPendientes = new ConcurrentHashMap<>();
    private volatile UsuarioDTO usuario;
    private volatile long sesionLocal = -1;

    public ServicioChat(IProtocoloCliente protocolo, IRepositorioMensajesLocal repo, CacheMensajesSesion cache,
                        ISerializador json) {
        this.protocolo = Objects.requireNonNull(protocolo, "protocolo");
        this.repo = Objects.requireNonNull(repo, "repo");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public void iniciarSesion(UsuarioDTO u, long sesionLocal) {
        Objects.requireNonNull(u, "u");
        sesion.lock();
        try {
            cache.limpiar();
            originalesPendientes.clear();
            this.usuario = u;
            this.sesionLocal = sesionLocal;
            while (!bufer.isEmpty()) {
                guardar(bufer.pollFirst());
            }
        } finally {
            sesion.unlock();
        }
    }

    @Override
    public void cerrarSesion() {
        sesion.lock();
        try {
            usuario = null;
            sesionLocal = -1;
            bufer.clear();
            cache.limpiar();
            originalesPendientes.clear();
        } finally {
            sesion.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> enviar(Canal canal, String destino, ContenidoDTO c) {
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(c, "c");
        byte[] original;
        try {
            if (canal == Canal.PRIVADO && (destino == null || destino.isBlank())) {
                throw new IllegalArgumentException("Falta el destinatario del mensaje privado");
            }
            original = validar(c);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        TipoComando comando = canal == Canal.FORO ? TipoComando.ENVIAR_FORO : TipoComando.ENVIAR_PRIVADO;
        Map<String, String> params = canal == Canal.FORO ? null : Map.of("destino", destino);
        return protocolo.solicitar(comando, params, c).thenAccept(r -> {
            String id = ExcepcionServidor.exigirExito(r).datos().path("idMensaje").asText(null);
            if (original != null && id != null) {
                originalesPendientes.put(id, new Original(c.nombreArchivo(), original));
                intentarGuardarOriginal(id);
            }
        });
    }

    @Override
    public void recibir(MensajeDTO m) {
        Objects.requireNonNull(m, "m");
        sesion.lock();
        try {
            if (usuario == null) {
                if (bufer.size() == MAX_BUFER) {
                    bufer.pollFirst();
                }
                bufer.addLast(m);
                return;
            }
            guardar(m);
        } finally {
            sesion.unlock();
        }
    }

    @Override
    public List<MensajeDTO> historial(String conversacion) {
        String clave = clave(conversacion);
        if (cache.contiene(clave)) {
            return cache.get(clave);
        }
        List<MensajeDTO> locales = repo.conversacion(clave);
        cache.cargar(clave, locales);
        return cache.get(clave);
    }

    @Override
    public CompletableFuture<List<MensajeDTO>> sincronizar(String conversacion) {
        clave(conversacion);
        return protocolo.solicitar(TipoComando.HISTORIAL, Map.of("conversacion", conversacion), null)
                .thenApplyAsync(r -> {
                    List<MensajeDTO> remotos = json.convertir(ExcepcionServidor.exigirExito(r).datos().get("mensajes"),
                            MENSAJES);
                    Set<String> conocidos = new HashSet<>();
                    historial(conversacion).forEach(m -> conocidos.add(m.id()));
                    var nuevos = new ArrayList<MensajeDTO>();
                    for (MensajeDTO m : remotos) {
                        if (conocidos.add(m.id())) {
                            recibir(m);
                            nuevos.add(m);
                        }
                    }
                    nuevos.sort(CacheMensajesSesion.CRONOLOGICO);
                    return List.copyOf(nuevos);
                });
    }

    @Override
    public CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
        Objects.requireNonNull(idMensaje, "idMensaje");
        Objects.requireNonNull(etapa, "etapa");
        Optional<byte[]> local = repo.adjunto(idMensaje, etapa);
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(local.get());
        }
        return protocolo.solicitar(TipoComando.OBTENER_IMAGEN, Map.of("idMensaje", idMensaje, "etapa", etapa.name()), null)
                .thenApplyAsync(r -> descargada(ExcepcionServidor.exigirExito(r), idMensaje, etapa));
    }

    @Override
    public long mensajesGuardados() {
        return usuario == null ? 0 : repo.contar();
    }

    private byte[] descargada(RespuestaDTO r, String idMensaje, TipoFiltro etapa) {
        byte[] datos = Base64.getDecoder().decode(r.datos().path("datosBase64").asText(""));
        String nombre = r.datos().path("nombreArchivo").asText(etapa.name().toLowerCase(Locale.ROOT));
        try {
            repo.guardarAdjunto(idMensaje, etapa, nombre, datos);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // El mensaje no está en H2 o la base ya se cerró: se entregan los bytes igual.
        }
        return datos;
    }

    /** Guarda en H2 y en la caché; se llama con {@code sesion} tomado y con usuario. */
    private void guardar(MensajeDTO m) {
        repo.guardar(m, sesionLocal, m.remitente().equals(usuario.codigo()));
        cache.agregar(m);
        if (originalesPendientes.containsKey(m.id())) {
            intentarGuardarOriginal(m.id());
        }
    }

    /** Guarda el original pendiente si su mensaje ya está en H2; si no, lo deja para {@link #guardar}. */
    private void intentarGuardarOriginal(String id) {
        Original o = originalesPendientes.get(id);
        if (o == null) {
            return;
        }
        try {
            repo.guardarAdjunto(id, TipoFiltro.ORIGINAL, o.nombre(), o.datos());
            originalesPendientes.remove(id, o);
        } catch (IllegalArgumentException e) {
            // Aún no llegó su MENSAJE_NUEVO (R-48): lo guardará guardar().
        } catch (IllegalStateException e) {
            // La sesión se cerró mientras tanto: el mensaje ya salió, solo se pierde la copia local.
            originalesPendientes.remove(id, o);
        }
    }

    private String clave(String conversacion) {
        Objects.requireNonNull(conversacion, "conversacion");
        if (IRepositorioMensajesLocal.FORO.equals(conversacion)) {
            return IRepositorioMensajesLocal.FORO;
        }
        UsuarioDTO u = usuario;
        if (u == null) {
            throw new IllegalStateException("No hay una sesión iniciada");
        }
        return IRepositorioMensajesLocal.claveConversacion(u.codigo(), conversacion);
    }

    /**
     * Mismas reglas que {@code ValidadorPeticiones} del servidor.
     *
     * @return los bytes originales si es una imagen; {@code null} si es texto.
     */
    static byte[] validar(ContenidoDTO c) {
        if (c.tipo() == TipoMensaje.TEXTO) {
            String t = c.texto();
            if (t == null || t.isBlank()) {
                throw new IllegalArgumentException("El mensaje está vacío");
            }
            int caracteres = t.codePointCount(0, t.length());
            if (caracteres > MAX_TEXTO_CARACTERES) {
                throw new IllegalArgumentException("El mensaje tiene " + caracteres + " caracteres (máximo "
                        + MAX_TEXTO_CARACTERES + ")");
            }
            if (c.nombreArchivo() != null || c.datosBase64() != null) {
                throw new IllegalArgumentException("Un mensaje de texto no lleva archivo");
            }
            return null;
        }
        String nombre = c.nombreArchivo();
        if (nombre == null || nombre.isBlank() || nombre.contains("/") || nombre.contains("\\") || nombre.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }
        int punto = nombre.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES.contains(extension)) {
            throw new IllegalArgumentException("Solo se aceptan imágenes png, jpg, jpeg, gif o bmp");
        }
        if (c.datosBase64() == null || c.datosBase64().isEmpty()) {
            throw new IllegalArgumentException("Faltan los datos de la imagen");
        }
        byte[] datos;
        try {
            datos = Base64.getDecoder().decode(c.datosBase64());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Los datos de la imagen no son Base64 válido", e);
        }
        if (datos.length == 0 || datos.length > MAX_IMAGEN_BYTES) {
            throw new IllegalArgumentException("La imagen debe pesar como máximo 5 MB");
        }
        return datos;
    }

    private record Original(String nombre, byte[] datos) { }
}
