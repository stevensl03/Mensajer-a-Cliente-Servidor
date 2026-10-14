package com.universidad.mensajeria.soporte.conexiones;

import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.UsuarioDTO;
import com.universidad.mensajeria.fachada.ICanalCliente;

import java.time.LocalDateTime;

/**
 * Una sesión activa en memoria.
 *
 * @param canal   canal del cliente.
 * @param usuario usuario autenticado.
 * @param desde   momento del login.
 */
public record SesionActiva(ICanalCliente canal, UsuarioDTO usuario, LocalDateTime desde) {

    /** @return la vista de la sesión para las capas superiores. */
    public SesionDTO aDTO() {
        return new SesionDTO(canal.getId(), usuario.codigo(), usuario.nombres() + " " + usuario.apellidos(),
                canal.getIpRemota(), desde);
    }
}
