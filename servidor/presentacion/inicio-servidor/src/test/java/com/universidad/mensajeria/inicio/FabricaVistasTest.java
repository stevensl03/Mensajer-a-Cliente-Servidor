package com.universidad.mensajeria.inicio;
import com.universidad.mensajeria.dto.ContenidoDTO;
import com.universidad.mensajeria.dto.EstadoUsuarioDTO;
import com.universidad.mensajeria.dto.SesionDTO;
import com.universidad.mensajeria.dto.TipoFiltro;
import com.universidad.mensajeria.dto.TipoInforme;
import com.universidad.mensajeria.fachada.IFachadaServidor;
import com.universidad.mensajeria.fachada.IObservadorServidor;
import com.universidad.mensajeria.presentacion.consola.VistaConsola;
import com.universidad.mensajeria.presentacion.escritorio.VentanaPrincipal;
import com.universidad.mensajeria.utilerias.SerializadorJSON;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class FabricaVistasTest {

    @Test
    void laConsolaQuedaSuscritaALaFachada() {
        var fachada = new FachadaFalsa();

        assertNotNull(FabricaVistas.crear(TipoVista.CONSOLA, fachada, new SerializadorJSON()));

        assertEquals(1, fachada.observadores.size());
        assertInstanceOf(VistaConsola.class, fachada.observadores.get(0));
    }

    @Test
    void elEscritorioQuedaSuscritoALaFachada() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sin entorno gráfico no se puede crear un JFrame");
        var fachada = new FachadaFalsa();

        FabricaVistas.crear(TipoVista.ESCRITORIO, fachada, new SerializadorJSON());

        assertInstanceOf(VentanaPrincipal.class, fachada.observadores.get(0));
    }

    /** Fachada que solo graba las suscripciones. */
    private static final class FachadaFalsa implements IFachadaServidor {
        final List<IObservadorServidor> observadores = new ArrayList<>();

        @Override
        public void suscribir(IObservadorServidor o) {
            observadores.add(o);
        }

        @Override
        public void iniciarServidor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void detenerServidor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publicarEnForo(ContenidoDTO c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enviarPrivado(String destino, ContenidoDTO c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String informe(TipoInforme t, Map<String, String> filtros) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SesionDTO> listarSesiones() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EstadoUsuarioDTO> listarUsuarios() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cerrarSesion(String idSesion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getIpServidor() {
            return "127.0.0.1";
        }

        @Override
        public Optional<byte[]> imagen(String idMensaje, TipoFiltro etapa) {
            return Optional.empty();
        }
    }
}
