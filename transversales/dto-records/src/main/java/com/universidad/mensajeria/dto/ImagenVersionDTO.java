package com.universidad.mensajeria.dto;

/**
 * Una versión de imagen del pipeline (orden 0 a 5). Refleja lo guardado en
 * disco y en la tabla {@code imagenes_mensaje} (C-07).
 *
 * @param ordenSecuencia 0 a 5 (0 = original).
 * @param etapaFiltro    ORIGINAL, ESCALA_GRISES, SEPIA, ROTAR_180, BRILLO o REDUCCION_TAMANO.
 * @param nombreArchivo  nombre del archivo en disco.
 * @param datos          bytes de la imagen.
 * @param tamanoBytes    tamaño en bytes.
 * @param ancho          ancho en píxeles.
 * @param alto           alto en píxeles.
 * @param rutaArchivo    copia en el directorio de almacenamiento.
 * @param hashSha256     hash SHA-256 de la versión.
 */
public record ImagenVersionDTO(int ordenSecuencia, String etapaFiltro, String nombreArchivo,
                               byte[] datos, int tamanoBytes, Integer ancho, Integer alto,
                               String rutaArchivo, String hashSha256) {
}
