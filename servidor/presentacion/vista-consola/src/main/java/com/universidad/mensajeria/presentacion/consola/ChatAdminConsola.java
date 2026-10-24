package com.universidad.mensajeria.presentacion.consola;

import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.TipoMensaje;
import com.universidad.mensajeria.fachada.IFachadaServidor;

import java.util.Objects;

/**
 * El administrador escribe en el foro o por privado desde la consola (solo
 * texto). El mensaje propio aparece cuando llega por {@code onMensaje}.
 */
public final class ChatAdminConsola {

    private final IFachadaServidor fachada;

    public ChatAdminConsola(IFachadaServidor fachada) {
        this.fachada = Objects.requireNonNull(fachada, "fachada");
    }

    public void publicarEnForo(String texto) {
        fachada.publicarEnForo(texto(texto));
    }

    public void enviarPrivado(String codigo, String texto) {
        fachada.enviarPrivado(codigo, texto(texto));
    }

    private static ContenidoDTO texto(String texto) {
        return new ContenidoDTO(TipoMensaje.TEXTO, texto, null, null);
    }
}
