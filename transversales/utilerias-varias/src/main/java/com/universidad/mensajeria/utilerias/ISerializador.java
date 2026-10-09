package com.universidad.mensajeria.utilerias;

/**
 * Conversión entre objetos y JSON. Se inyecta en los componentes que lo
 * necesitan (inversión de dependencias).
 */
public interface ISerializador {

    /**
     * @param o objeto a serializar.
     * @return el JSON del objeto.
     * @throws IllegalArgumentException si no se puede serializar.
     */
    String aJson(Object o);

    /**
     * @param s    JSON de entrada.
     * @param tipo clase destino.
     * @param <T>  tipo destino.
     * @return el objeto leído.
     * @throws IllegalArgumentException si el JSON es inválido o no corresponde al tipo.
     */
    <T> T desdeJson(String s, Class<T> tipo);
}
