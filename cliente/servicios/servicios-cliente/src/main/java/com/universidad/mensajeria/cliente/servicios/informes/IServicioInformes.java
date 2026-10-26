package com.universidad.mensajeria.cliente.servicios.informes;

import com.universidad.mensajeria.dto.TipoInforme;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Comando INFORME: el cliente ve los 4 informes del servidor. */
public interface IServicioInformes {

    /**
     * @param t       tipo de informe.
     * @param filtros {@code tipoEvento}, {@code usuario}, {@code desde}, {@code hasta}
     *                (solo los usa LOGS; los vacíos no se envían). Puede ser {@code null}.
     * @return el informe con su JSON (D-08).
     */
    CompletableFuture<InformeRecibido> obtener(TipoInforme t, Map<String, String> filtros);
}
