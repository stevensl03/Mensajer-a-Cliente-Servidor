package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.MensajeDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HISTORIAL {conversacion: "FORO" | codigo, limite?}: los últimos
 * {@code limite} mensajes (200 por defecto), ordenados por fecha.
 */
public final class ManejadorHistorial implements IManejadorComando {

    static final String FORO = "FORO";
    static final int LIMITE_POR_DEFECTO = 200;

    private final IAlmacenMensajes mensajes;
    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorHistorial(IAlmacenMensajes mensajes, IUsuariosDisponibles usuarios,
                              IGestorConexiones conexiones, IClienteServidor clienteServidor) {
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        String conversacion = IManejadorComando.requerido(p, "conversacion");
        int limite = limite(p);
        List<MensajeDTO> lista;
        if (FORO.equals(conversacion)) {
            lista = mensajes.foro();
        } else {
            if (!usuarios.existe(conversacion)) {
                throw new ExcepcionNegocio(CodigoError.DESTINO_INEXISTENTE, "El usuario no existe: " + conversacion);
            }
            String yo = conexiones.usuarioDe(c.getId())
                    .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO, "Primero debe iniciar sesión"))
                    .codigo();
            lista = mensajes.conversacion(yo, conversacion);
        }
        List<MensajeDTO> ultimos = lista.subList(Math.max(0, lista.size() - limite), lista.size());
        return clienteServidor.exito(p.idPeticion(), Map.of("mensajes", List.copyOf(ultimos)));
    }

    private static int limite(PeticionDTO p) {
        try {
            int limite = p.parametro("limite").map(Integer::parseInt).orElse(LIMITE_POR_DEFECTO);
            if (limite <= 0) {
                throw new NumberFormatException("no positivo");
            }
            return limite;
        } catch (NumberFormatException e) {
            throw new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "limite debe ser un entero positivo");
        }
    }
}
