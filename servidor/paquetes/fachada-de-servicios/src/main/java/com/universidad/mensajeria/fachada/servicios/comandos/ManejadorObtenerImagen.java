package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.Canal;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ImagenVersionDTO;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * OBTENER_IMAGEN {idMensaje, etapa}: solo si el mensaje es del FORO o el
 * usuario es su remitente o destinatario. Si no existe o no tiene acceso,
 * NO_ENCONTRADO (no se revela que exista).
 */
public final class ManejadorObtenerImagen implements IManejadorComando {

    private final IAlmacenMensajes mensajes;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorObtenerImagen(IAlmacenMensajes mensajes, IGestorConexiones conexiones,
                                  IClienteServidor clienteServidor) {
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        String idMensaje = IManejadorComando.requerido(p, "idMensaje");
        TipoFiltro etapa = etapa(IManejadorComando.requerido(p, "etapa"));
        String yo = conexiones.usuarioDe(c.getId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO, "Primero debe iniciar sesión"))
                .codigo();
        MensajeDTO m = mensajes.buscar(idMensaje)
                .filter(x -> x.tipo() == TipoMensaje.IMAGEN)
                .filter(x -> x.canal() == Canal.FORO || yo.equals(x.remitente()) || yo.equals(x.destinatario()))
                .orElseThrow(ManejadorObtenerImagen::noEncontrada);
        byte[] datos = mensajes.imagen(idMensaje, etapa).orElseThrow(ManejadorObtenerImagen::noEncontrada);
        String nombre = m.imagenes().stream()
                .filter(v -> v.etapa() == etapa)
                .map(ImagenVersionDTO::nombreArchivo)
                .findFirst()
                .orElse(m.nombreArchivo());
        return clienteServidor.exito(p.idPeticion(), Map.of(
                "idMensaje", idMensaje,
                "etapa", etapa,
                "nombreArchivo", nombre,
                "datosBase64", Base64.getEncoder().encodeToString(datos)));
    }

    private static TipoFiltro etapa(String valor) {
        try {
            return TipoFiltro.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "Etapa desconocida: " + valor);
        }
    }

    private static ExcepcionNegocio noEncontrada() {
        return new ExcepcionNegocio(CodigoError.NO_ENCONTRADO, "La imagen no existe");
    }
}
