package com.universidad.mensajeria.componentes.canal;

import com.universidad.mensajeria.dto.CodigoError;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.PeticionDTO;
import com.universidad.mensajeria.dto.TipoComando;
import com.universidad.mensajeria.dto.TipoEvento;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Parámetros obligatorios por comando y límites de contenido (§7).
 *
 * <p>Un parámetro que falta o no tiene el formato esperado da
 * {@code PETICION_INVALIDA}; un {@code contenido} inválido en ENVIAR_FORO o
 * ENVIAR_PRIVADO da {@code CONTENIDO_INVALIDO}.</p>
 */
public final class ValidadorPeticiones {

    private static final Set<String> EXTENSIONES = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    private final EnumMap<TipoComando, Set<String>> requeridos = new EnumMap<>(TipoComando.class);
    private final int maxTextoCaracteres;
    private final int maxImagenBytes;

    /**
     * @param maxTextoCaracteres {@code mensajes.maxTextoCaracteres}.
     * @param maxImagenBytes     {@code mensajes.maxImagenBytes} (bytes ya decodificados).
     */
    public ValidadorPeticiones(int maxTextoCaracteres, int maxImagenBytes) {
        if (maxTextoCaracteres <= 0 || maxImagenBytes <= 0) {
            throw new IllegalArgumentException("Los límites de contenido deben ser mayores que 0");
        }
        this.maxTextoCaracteres = maxTextoCaracteres;
        this.maxImagenBytes = maxImagenBytes;
        for (TipoComando c : TipoComando.values()) {
            requeridos.put(c, Set.of());
        }
        requeridos.put(TipoComando.LOGIN, Set.of("codigo", "clave"));
        requeridos.put(TipoComando.ENVIAR_PRIVADO, Set.of("destino"));
        requeridos.put(TipoComando.HISTORIAL, Set.of("conversacion"));
        requeridos.put(TipoComando.OBTENER_IMAGEN, Set.of("idMensaje", "etapa"));
        requeridos.put(TipoComando.INFORME, Set.of("tipo"));
    }

    /**
     * @param p petición a validar.
     * @throws PeticionInvalidaException si faltan parámetros o el contenido no es válido.
     */
    public void validar(PeticionDTO p) {
        for (String nombre : requeridos.get(p.comando())) {
            if (p.parametro(nombre).isEmpty()) {
                throw invalida(p, "Falta el parámetro '" + nombre + "' para " + p.comando());
            }
        }
        switch (p.comando()) {
            case ENVIAR_FORO, ENVIAR_PRIVADO -> validarContenido(p);
            case HISTORIAL -> p.parametro("limite").ifPresent(l -> entero(p, "limite", l));
            case OBTENER_IMAGEN -> valor(p, "etapa", TipoFiltro::valueOf);
            case INFORME -> {
                valor(p, "tipo", TipoInforme::valueOf);
                p.parametro("tipoEvento").ifPresent(v -> valor(p, "tipoEvento", TipoEvento::valueOf));
                p.parametro("desde").ifPresent(v -> valor(p, "desde", LocalDateTime::parse));
                p.parametro("hasta").ifPresent(v -> valor(p, "hasta", LocalDateTime::parse));
            }
            default -> {
                // LOGIN, LOGOUT y LISTAR_USUARIOS solo exigen sus parámetros.
            }
        }
    }

    private void validarContenido(PeticionDTO p) {
        ContenidoDTO c = p.contenido();
        if (c == null) {
            throw contenido(p, "Falta el contenido del mensaje");
        }
        switch (c.tipo()) {
            case TEXTO -> {
                if (c.texto() == null || c.texto().isBlank()) {
                    throw contenido(p, "El texto está vacío");
                }
                int caracteres = c.texto().codePointCount(0, c.texto().length());
                if (caracteres > maxTextoCaracteres) {
                    throw contenido(p, "El texto tiene " + caracteres + " caracteres (máximo " + maxTextoCaracteres + ")");
                }
                if (c.nombreArchivo() != null || c.datosBase64() != null) {
                    throw contenido(p, "Un mensaje de TEXTO no lleva nombreArchivo ni datosBase64");
                }
            }
            case IMAGEN -> validarImagen(p, c);
        }
    }

    private void validarImagen(PeticionDTO p, ContenidoDTO c) {
        String nombre = c.nombreArchivo();
        if (nombre == null || nombre.isBlank() || nombre.contains("/") || nombre.contains("\\")
                || nombre.contains("..")) {
            throw contenido(p, "Nombre de archivo inválido");
        }
        int punto = nombre.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONES.contains(extension)) {
            throw contenido(p, "Extensión no permitida: se aceptan " + String.join(", ", EXTENSIONES.stream().sorted().toList()));
        }
        String base64 = c.datosBase64();
        if (base64 == null || base64.isEmpty()) {
            throw contenido(p, "Faltan los datos de la imagen");
        }
        // Cota antes de decodificar, para no reservar memoria de más.
        if ((long) base64.length() / 4 * 3 > (long) maxImagenBytes + 3) {
            throw contenido(p, "La imagen supera el máximo de " + maxImagenBytes + " bytes");
        }
        byte[] datos;
        try {
            datos = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw contenido(p, "datosBase64 no es Base64 válido");
        }
        if (datos.length == 0 || datos.length > maxImagenBytes) {
            throw contenido(p, "La imagen debe tener entre 1 y " + maxImagenBytes + " bytes");
        }
        if (!legible(datos)) {
            throw contenido(p, "Los datos no son una imagen legible");
        }
    }

    /** Lee solo la cabecera con ImageIO: comprueba el formato y las dimensiones sin decodificar la imagen. */
    private static boolean legible(byte[] datos) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                return false;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada);
                return lector.getWidth(0) > 0 && lector.getHeight(0) > 0;
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void entero(PeticionDTO p, String nombre, String valor) {
        int n;
        try {
            n = Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            throw invalida(p, "El parámetro '" + nombre + "' debe ser un entero");
        }
        if (n <= 0) {
            throw invalida(p, "El parámetro '" + nombre + "' debe ser mayor que 0");
        }
    }

    private static void valor(PeticionDTO p, String nombre, Function<String, ?> conversion) {
        String v = p.parametro(nombre).orElseThrow();
        try {
            conversion.apply(v.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalida(p, "Valor inválido para '" + nombre + "': " + v);
        }
    }

    private static PeticionInvalidaException invalida(PeticionDTO p, String mensaje) {
        return new PeticionInvalidaException(p.idPeticion(), CodigoError.PETICION_INVALIDA, mensaje);
    }

    private static PeticionInvalidaException contenido(PeticionDTO p, String mensaje) {
        return new PeticionInvalidaException(p.idPeticion(), CodigoError.CONTENIDO_INVALIDO, mensaje);
    }
}
