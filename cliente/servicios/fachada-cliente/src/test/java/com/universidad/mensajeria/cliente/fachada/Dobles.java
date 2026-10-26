package com.universidad.mensajeria.cliente.fachada;

import com.universidad.mensajeria.cliente.protocolo.IOyentePush;
import com.universidad.mensajeria.cliente.protocolo.IProtocoloCliente;
import com.universidad.mensajeria.cliente.servicios.chat.IServicioChat;
import com.universidad.mensajeria.cliente.servicios.informes.IServicioInformes;
import com.universidad.mensajeria.cliente.servicios.informes.InformeRecibido;
import com.universidad.mensajeria.cliente.servicios.usuarios.IServicioUsuarios;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Dobles de los servicios y del protocolo que graban las llamadas. */
final class Dobles {

    private Dobles() {
    }

    static final class Usuarios implements IServicioUsuarios {
        final List<String> llamadas = new CopyOnWriteArrayList<>();
        CompletableFuture<UsuarioDTO> respuestaLogin;
        volatile UsuarioDTO actual;
        List<EstadoUsuarioDTO> lista = List.of();

        @Override
        public CompletableFuture<UsuarioDTO> login(String codigo, String clave, int puerto) {
            llamadas.add("login " + codigo + " " + puerto);
            return respuestaLogin.thenApply(u -> {
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
        public CompletableFuture<List<EstadoUsuarioDTO>> listar() {
            llamadas.add("listar");
            return CompletableFuture.completedFuture(lista);
        }

        @Override
        public Optional<UsuarioDTO> actual() {
            return Optional.ofNullable(actual);
        }

        @Override
        public long sesionLocal() {
            return actual == null ? -1 : 7;
        }
    }

    static final class Chat implements IServicioChat {
        final List<String> llamadas = new CopyOnWriteArrayList<>();
        final List<MensajeDTO> recibidos = new CopyOnWriteArrayList<>();
        List<MensajeDTO> locales = List.of();
        CompletableFuture<List<MensajeDTO>> sincronizacion = new CompletableFuture<>();

        @Override
        public void iniciarSesion(UsuarioDTO u, long sesionLocal) {
            llamadas.add("iniciarSesion " + u.codigo() + " " + sesionLocal);
        }

        @Override
        public void cerrarSesion() {
            llamadas.add("cerrarSesion");
        }

        @Override
        public CompletableFuture<Void> enviar(Canal canal, String destino, ContenidoDTO c) {
            llamadas.add("enviar " + canal + " " + destino + " " + c.texto());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void recibir(MensajeDTO m) {
            recibidos.add(m);
        }

        @Override
        public List<MensajeDTO> historial(String conversacion) {
            llamadas.add("historial " + conversacion);
            return locales;
        }

        @Override
        public CompletableFuture<List<MensajeDTO>> sincronizar(String conversacion) {
            llamadas.add("sincronizar " + conversacion);
            return sincronizacion;
        }

        @Override
        public CompletableFuture<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
            llamadas.add("imagen " + idMensaje + " " + etapa);
            return CompletableFuture.completedFuture(new byte[] {1});
        }

        @Override
        public long mensajesGuardados() {
            return 42;
        }
    }

    static final class Informes implements IServicioInformes {
        final List<String> llamadas = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<InformeRecibido> obtener(TipoInforme t, Map<String, String> filtros) {
            llamadas.add("informe " + t + " " + filtros);
            return CompletableFuture.completedFuture(
                    new InformeRecibido(new InformeDTO(t, LocalDateTime.now(), "x", List.of()), "{}"));
        }
    }

    /** Como el real: {@code desconectar()} termina avisando {@code onDesconexion} al oyente. */
    static final class Protocolo implements IProtocoloCliente {
        final AtomicInteger conexiones = new AtomicInteger();
        final AtomicInteger desconexiones = new AtomicInteger();
        volatile IOyentePush oyente;

        @Override
        public String conectar(String host, int puerto) {
            conexiones.incrementAndGet();
            return "192.168.20.31";
        }

        @Override
        public CompletableFuture<RespuestaDTO> solicitar(TipoComando c, Map<String, String> params, ContenidoDTO contenido) {
            throw new UnsupportedOperationException("La fachada no pide comandos directamente");
        }

        @Override
        public void suscribir(IOyentePush o) {
            oyente = o;
        }

        @Override
        public void desconectar() {
            desconexiones.incrementAndGet();
            oyente.onDesconexion("Conexión cerrada por el cliente");
        }
    }

    /** Observador que graba los avisos. */
    static class Observador implements IObservadorCliente {
        final List<MensajeDTO> mensajes = new CopyOnWriteArrayList<>();
        final List<List<EstadoUsuarioDTO>> usuarios = new CopyOnWriteArrayList<>();
        final List<String> desconexiones = new CopyOnWriteArrayList<>();

        @Override
        public void onMensaje(MensajeDTO m) {
            mensajes.add(m);
        }

        @Override
        public void onUsuarios(List<EstadoUsuarioDTO> l) {
            usuarios.add(l);
        }

        @Override
        public void onDesconexion(String motivo) {
            desconexiones.add(motivo);
        }
    }
}
