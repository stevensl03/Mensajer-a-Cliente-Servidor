package com.universidad.mensajeria.fachada.servicios;

import com.universidad.mensajeria.componentes.usuarios.IUsuariosDisponibles;
import com.universidad.mensajeria.dto.Rol;
import com.universidad.mensajeria.dto.UsuarioDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Catálogo fijo de usuarios para las pruebas. La clave de cada uno es "clave" + su nombre. */
final class UsuariosFalso implements IUsuariosDisponibles {

    static final UsuarioDTO ANA = new UsuarioDTO("20201001", "Ana", "Pérez", "Sistemas", Rol.USUARIO);
    static final UsuarioDTO LUIS = new UsuarioDTO("20201002", "Luis", "Gómez", "Civil", Rol.USUARIO);
    static final UsuarioDTO MARIA = new UsuarioDTO("20201003", "María", "Rodríguez", "Electrónica", Rol.USUARIO);
    static final UsuarioDTO ADMIN = new UsuarioDTO("00000000", "Admin", "Servidor", "Sistemas", Rol.ADMIN);

    private static final Map<String, UsuarioDTO> POR_CODIGO = Map.of(
            ANA.codigo(), ANA, LUIS.codigo(), LUIS, MARIA.codigo(), MARIA, ADMIN.codigo(), ADMIN);

    static String clave(UsuarioDTO u) {
        return "clave" + u.nombres();
    }

    @Override
    public Optional<UsuarioDTO> autenticar(String codigo, String clave) {
        return buscar(codigo).filter(u -> clave(u).equals(clave));
    }

    @Override
    public List<UsuarioDTO> listarRegistrados() {
        return List.of(ADMIN, ANA, LUIS, MARIA);
    }

    @Override
    public Optional<UsuarioDTO> buscar(String codigo) {
        return Optional.ofNullable(POR_CODIGO.get(codigo));
    }

    @Override
    public boolean existe(String codigo) {
        return POR_CODIGO.containsKey(codigo);
    }

    @Override
    public UsuarioDTO administrador() {
        return ADMIN;
    }
}
