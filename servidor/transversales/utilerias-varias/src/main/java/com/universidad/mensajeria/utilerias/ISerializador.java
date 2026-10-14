package com.universidad.mensajeria.utilerias;

import com.fasterxml.jackson.databind.JsonNode;

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

    /**
     * @param o objeto a convertir.
     * @return el árbol JSON del objeto.
     */
    JsonNode aArbol(Object o);

    /**
     * @param n    árbol JSON.
     * @param tipo clase destino.
     * @param <T>  tipo destino.
     * @return el objeto leído del árbol.
     * @throws IllegalArgumentException si el árbol no corresponde al tipo.
     */
    <T> T convertir(JsonNode n, Class<T> tipo);
}
