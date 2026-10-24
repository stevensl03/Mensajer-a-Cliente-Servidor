package com.universidad.mensajeria.fachada.servicios.comandos;

import com.universidad.mensajeria.componentes.canal.IClienteServidor;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.RespuestaDTO;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.fachada.ICanalCliente;
import com.universidad.mensajeria.fachada.servicios.ExcepcionNegocio;
import com.universidad.mensajeria.fachada.servicios.GeneradorInformes;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;

import java.util.Objects;

/**
 * INFORME {tipo, tipoEvento?, usuario?, desde?, hasta?}: responde el
 * {@code InformeDTO}. El cliente ve todos los informes.
 */
public final class ManejadorInforme implements IManejadorComando {

    private final GeneradorInformes informes;
    private final IGestorConexiones conexiones;
    private final IClienteServidor clienteServidor;

    public ManejadorInforme(GeneradorInformes informes, IGestorConexiones conexiones,
                            IClienteServidor clienteServidor) {
        this.informes = Objects.requireNonNull(informes, "informes");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.clienteServidor = Objects.requireNonNull(clienteServidor, "clienteServidor");
    }

    @Override
    public RespuestaDTO ejecutar(PeticionDTO p, ICanalCliente c) {
        TipoInforme tipo = tipo(IManejadorComando.requerido(p, "tipo"));
        String yo = conexiones.usuarioDe(c.getId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoError.NO_AUTENTICADO, "Primero debe iniciar sesión"))
                .codigo();
        return clienteServidor.exito(p.idPeticion(),
                GeneradorInformes.firmar(informes.generar(tipo, p.parametros()), yo));
    }

    private static TipoInforme tipo(String valor) {
        try {
            return TipoInforme.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "Tipo de informe desconocido: " + valor);
        }
    }
}
