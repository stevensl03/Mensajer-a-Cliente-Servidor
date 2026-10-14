package com.universidad.mensajeria.componentes.usuarios;

import com.universidad.mensajeria.dto.UsuarioRegistroDTO;
import com.universidad.mensajeria.utilerias.IGeneradorHash;

import java.util.List;

/**
 * Crea el componente Usuarios (Factory). Recibe la lista ya cargada: la
 * entrega el Composition Root, así Usuarios no conoce la persistencia.
 */
public final class FabricaUsuarios {

    private FabricaUsuarios() {
    }

    /**
     * @param registrados usuarios registrados.
     * @param h           generador de hash.
     * @return el catálogo de usuarios.
     */
    public static IUsuariosDisponibles crear(List<UsuarioRegistroDTO> registrados, IGeneradorHash h) {
        return new ServicioUsuarios(registrados, h);
    }
}
