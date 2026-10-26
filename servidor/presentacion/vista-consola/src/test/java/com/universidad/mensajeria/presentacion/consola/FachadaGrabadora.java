package com.universidad.mensajeria.presentacion.consola;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Fachada de prueba que graba cada llamada como texto. */
final class FachadaGrabadora implements IFachadaServidor {

    final List<String> llamadas = new ArrayList<>();
    final List<SesionDTO> sesiones = new ArrayList<>(List.of(
            new SesionDTO("abcd-1111", "20201001", "Ana Pérez", "10.0.0.5", LocalDateTime.of(2026, 10, 13, 10, 0)),
            new SesionDTO("efgh-2222", "20201002", "Luis Gómez", "10.0.0.6", LocalDateTime.of(2026, 10, 13, 10, 5))));
    RuntimeException fallo;

    @Override
    public void iniciarServidor() {
        llamadas.add("iniciar");
    }

    @Override
    public void detenerServidor() {
        llamadas.add("detener");
    }

    @Override
    public void publicarEnForo(ContenidoDTO c) {
        lanzarSiHayFallo();
        llamadas.add("foro:" + c.texto());
    }

    @Override
    public void enviarPrivado(String destino, ContenidoDTO c) {
        lanzarSiHayFallo();
        llamadas.add("privado:" + destino + ":" + c.texto());
    }

    @Override
    public String informe(TipoInforme t, Map<String, String> filtros) {
        llamadas.add("informe:" + t + (filtros.isEmpty() ? "" : ":" + new TreeMap<>(filtros)));
        return "{\"tipo\":\"" + t + "\",\"filas\":[]}";
    }

    @Override
    public List<SesionDTO> listarSesiones() {
        llamadas.add("sesiones");
        return List.copyOf(sesiones);
    }

    @Override
    public List<EstadoUsuarioDTO> listarUsuarios() {
        return List.of();
    }

    @Override
    public void cerrarSesion(String idSesion) {
        llamadas.add("cerrar:" + idSesion);
    }

    @Override
    public void suscribir(IObservadorServidor o) {
        llamadas.add("suscribir");
    }

    @Override
    public String getIpServidor() {
        return "192.168.1.10";
    }

    @Override
    public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
        return Optional.empty();
    }

    private void lanzarSiHayFallo() {
        if (fallo != null) {
            throw fallo;
        }
    }
}
