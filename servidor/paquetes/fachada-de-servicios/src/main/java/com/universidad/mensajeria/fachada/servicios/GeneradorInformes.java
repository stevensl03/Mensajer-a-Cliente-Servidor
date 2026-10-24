package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.InformeDTO;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.soporte.conexiones.IGestorConexiones;
import com.universidad.mensajeria.soporte.log.FiltroLogs;
import com.universidad.mensajeria.soporte.log.ILogEventos;
import com.universidad.mensajeria.soporte.mensajes.IAlmacenMensajes;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Arma los 4 informes desde las estructuras de datos en memoria (§6.5). El
 * informe LOGS acepta los filtros {@code tipoEvento}, {@code usuario},
 * {@code desde} y {@code hasta}.
 */
public final class GeneradorInformes {

    private final IUsuariosDisponibles usuarios;
    private final IGestorConexiones conexiones;
    private final IAlmacenMensajes mensajes;
    private final ILogEventos log;

    public GeneradorInformes(IUsuariosDisponibles usuarios, IGestorConexiones conexiones,
                             IAlmacenMensajes mensajes, ILogEventos log) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
        this.mensajes = Objects.requireNonNull(mensajes, "mensajes");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * @param t       tipo de informe.
     * @param filtros filtros opcionales (solo los usa LOGS).
     * @return el informe, sin {@code generadoPor} (lo completa quien lo pide con {@link #firmar}).
     * @throws ExcepcionNegocio PETICION_INVALIDA si un filtro no tiene formato válido.
     */
    public InformeDTO generar(TipoInforme t, Map<String, String> filtros) {
        Objects.requireNonNull(t, "t");
        Map<String, String> f = filtros == null ? Map.of() : filtros;
        List<?> filas = switch (t) {
            case USUARIOS_REGISTRADOS -> usuarios.listarRegistrados();
            case USUARIOS_CONECTADOS -> conexiones.conteoPorUsuario();
            case MENSAJES -> mensajes.todos();
            case LOGS -> log.consultar(new FiltroLogs(
                    leer(f, "tipoEvento", TipoEvento::valueOf),
                    leer(f, "usuario", Function.identity()),
                    leer(f, "desde", LocalDateTime::parse),
                    leer(f, "hasta", LocalDateTime::parse)));
        };
        return new InformeDTO(t, LocalDateTime.now(), null, filas);
    }

    /**
     * @param i           informe generado.
     * @param generadoPor código de quien lo pidió.
     * @return el mismo informe con {@code generadoPor}.
     */
    public static InformeDTO firmar(InformeDTO i, String generadoPor) {
        return new InformeDTO(i.tipo(), i.generado(), generadoPor, i.filas());
    }

    private static <T> T leer(Map<String, String> f, String nombre, Function<String, T> conversion) {
        String valor = f.get(nombre);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return conversion.apply(valor);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new ExcepcionNegocio(CodigoError.PETICION_INVALIDA, "Filtro inválido " + nombre + "=" + valor);
        }
    }
}
