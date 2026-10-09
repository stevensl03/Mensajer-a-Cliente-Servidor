package com.universidad.mensajeria.dto;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Petición de un cliente. Viaja como JSON dentro de una trama
 * {@code [int32 longitud][JSON UTF-8]}.
 *
 * @param idPeticion identificador que el servidor devuelve en la respuesta.
 * @param comando    comando solicitado.
 * @param parametros parámetros del comando (por ejemplo {@code codigo} y {@code clave}).
 * @param contenido  contenido del mensaje, o {@code null} si el comando no lo usa.
 */
public record PeticionDTO(String idPeticion, TipoComando comando,
                          Map<String, String> parametros, ContenidoDTO contenido) {

    public PeticionDTO {
        Objects.requireNonNull(idPeticion, "idPeticion");
        Objects.requireNonNull(comando, "comando");
        parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
    }

    /**
     * @param nombre nombre del parámetro.
     * @return el valor del parámetro si existe y no está en blanco.
     */
    public Optional<String> parametro(String nombre) {
        return Optional.ofNullable(parametros.get(nombre)).filter(v -> !v.isBlank());
    }
}
