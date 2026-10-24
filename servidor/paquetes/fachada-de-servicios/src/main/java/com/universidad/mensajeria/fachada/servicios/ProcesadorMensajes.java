package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.gestor.IConsumidorMensajes;
import com.universidad.mensajeria.componentes.servicios.IServiciosDisponibles;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.SolicitudMensajeDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.soporte.log.ILogEventos;
import com.universidad.mensajeria.soporte.mensajes.FabricaMensajes;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;
import com.universidad.mensajeria.soporte.mensajes.Mensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

/**
 * Lo ejecutan los hilos consumidores de la cola (§6.3, paso 5): procesa el
 * contenido, crea el Mensaje, lo guarda, lo registra y lo entrega. Si algo
 * falla, registra un ERROR y no envía push.
 */
public final class ProcesadorMensajes implements IConsumidorMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(ProcesadorMensajes.class);

    private final IServiciosDisponibles servicios;
    private final FabricaMensajes fabrica;
    private final IAlmacenMensajes almacen;
    private final ILogEventos log;
    private final EnrutadorMensajes enrutador;

    public ProcesadorMensajes(IServiciosDisponibles servicios, FabricaMensajes fabrica, IAlmacenMensajes almacen,
                              ILogEventos log, EnrutadorMensajes enrutador) {
        this.servicios = Objects.requireNonNull(servicios, "servicios");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.almacen = Objects.requireNonNull(almacen, "almacen");
        this.log = Objects.requireNonNull(log, "log");
        this.enrutador = Objects.requireNonNull(enrutador, "enrutador");
    }

    @Override
    public void procesar(SolicitudMensajeDTO s) {
        MensajeDTO guardado;
        try {
            guardado = almacen.agregar(crear(s));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo procesar el mensaje {}", s.idMensaje(), e);
            log.registrar(TipoEvento.ERROR, "ERROR id=" + s.idMensaje() + " de=" + s.remitente()
                    + " causa=" + e.getClass().getSimpleName(), s.remitente(), null);
            return;
        }
        TipoEvento tipo = guardado.canal() == Canal.FORO ? TipoEvento.MENSAJE_FORO : TipoEvento.MENSAJE_PRIVADO;
        log.registrar(tipo, descripcion(tipo, guardado), guardado.remitente(), null);
        enrutador.entregar(guardado);
    }

    private Mensaje crear(SolicitudMensajeDTO s) {
        ContenidoDTO c = s.contenido();
        if (c.tipo() == TipoMensaje.TEXTO) {
            return fabrica.deTexto(s, servicios.procesarTexto(c.texto()));
        }
        byte[] datos = Base64.getDecoder().decode(c.datosBase64());
        return fabrica.deImagen(s, servicios.procesarImagen(datos, c.nombreArchivo()));
    }

    /** Formato de §10; nunca incluye el contenido completo. */
    private static String descripcion(TipoEvento tipo, MensajeDTO m) {
        var d = new StringBuilder(tipo.name())
                .append(" id=").append(m.id())
                .append(" de=").append(m.remitente());
        if (m.canal() == Canal.PRIVADO) {
            d.append(" para=").append(m.destinatario());
        }
        d.append(" tipo=").append(m.tipo());
        if (m.tipo() == TipoMensaje.TEXTO) {
            d.append(" hash=").append(m.hash())
                    .append(" caracteres=").append(m.numCaracteres())
                    .append(" palabras=").append(m.numPalabras());
        } else {
            d.append(" archivo=").append(m.nombreArchivo())
                    .append(" hash=").append(m.hash())
                    .append(" filtros=").append(Math.max(0, m.imagenes().size() - 1));
        }
        return d.toString();
    }
}
