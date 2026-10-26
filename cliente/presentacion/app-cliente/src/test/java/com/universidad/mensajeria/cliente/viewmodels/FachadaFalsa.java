package com.universidad.mensajeria.cliente.viewmodels;

import com.universidad.mensajeria.cliente.fachada.IFachadaCliente;
import com.universidad.mensajeria.cliente.fachada.IObservadorCliente;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/** Fachada para probar los ViewModels: graba las llamadas y responde lo que la prueba configure. */
public class FachadaFalsa implements IFachadaCliente {

    public final List<String> llamadas = new CopyOnWriteArrayList<>();
    public final List<IObservadorCliente> observadores = new CopyOnWriteArrayList<>();
    public RuntimeException errorConectar;
    public BiFunction<String, String, CompletableFuture<UsuarioDTO>> login =
            (c, k) -> CompletableFuture.failedFuture(new IllegalStateException("login no configurado"));
    public volatile UsuarioDTO actual;
    public final Map<String, List<MensajeDTO>> historiales = new java.util.concurrent.ConcurrentHashMap<>();
    public volatile List<EstadoUsuarioDTO> listaUsuarios = List.of();
    /** Respuesta de los envíos (por defecto, ENCOLADO). */
    public java.util.function.Supplier<CompletableFuture<Void>> respuestaEnvio = () -> CompletableFuture.completedFuture(null);
    public final List<ContenidoDTO> contenidos = new CopyOnWriteArrayList<>();
    public volatile long guardados;

    @Override
    public String conectar(String host, int puerto) {
        llamadas.add("conectar " + host + ":" + puerto);
        if (errorConectar != null) {
            throw errorConectar;
        }
        return "192.168.20.31";
    }

    @Override
    public CompletableFuture<UsuarioDTO> login(String codigo, String clave) {
        llamadas.add("login " + codigo + " " + clave);
        return login.apply(codigo, clave).thenApply(u -> {
            actual = u;
            return u;
        });
    }

    @Override
    public void logout() {
        llamadas.add("logout");
        actual = null;
    }

    @Override
    public CompletableFuture<List<EstadoUsuarioDTO>> usuarios() {
        llamadas.add("usuarios");
        return CompletableFuture.completedFuture(listaUsuarios);
    }

    @Override
    public CompletableFuture<Void> enviarForo(ContenidoDTO c) {
        llamadas.add("enviarForo");
        contenidos.add(c);
        return respuestaEnvio.get();
    }

    @Override
    public CompletableFuture<Void> enviarPrivado(String destino, ContenidoDTO c) {
        llamadas.add("enviarPrivado " + destino);
        contenidos.add(c);
        return respuestaEnvio.get();
    }

    @Override
    public List<MensajeDTO> historial(String conversacion) {
        llamadas.add("historial " + conversacion);
        return historiales.getOrDefault(conversacion, List.of());
    }

    @Override
    public CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
        llamadas.add("imagen " + idMensaje + " " + etapa);
        return CompletableFuture.completedFuture(new byte[0]);
    }

    @Override
    public CompletableFuture<InformeRecibido> informe(TipoInforme t, Map<String, String> filtros) {
        llamadas.add("informe " + t);
        return CompletableFuture.failedFuture(new IllegalStateException("informe no configurado"));
    }

    @Override
    public String getIpServidor() {
        return "192.168.20.31";
    }

    @Override
    public int getPuerto() {
        return 5000;
    }

    @Override
    public void suscribir(IObservadorCliente o) {
        observadores.add(o);
    }

    @Override
    public Optional<UsuarioDTO> usuarioActual() {
        return Optional.ofNullable(actual);
    }

    @Override
    public long sesionLocal() {
        return actual == null ? -1 : 3;
    }

    @Override
    public long mensajesGuardados() {
        return guardados;
    }
}
