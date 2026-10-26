package com.universidad.mensajeria.cliente.protocolo;

import com.universidad.mensajeria.cliente.comunicacion.IConexionServidor;
import com.universidad.mensajeria.cliente.comunicacion.IReceptorServidor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Transporte en memoria: graba lo enviado y deja simular tramas y caídas del servidor. */
final class ConexionFalsa implements IConexionServidor {

    static final String INFO = """
            {"evento":"INFO_SERVIDOR","fecha":"2026-10-25T19:59:57.1","datos":
             {"cuposLibres":10,"protocolo":"TCP","ipServidor":"192.168.20.31","puerto":5000}}""";

    final BlockingQueue<String> enviados = new LinkedBlockingQueue<>();
    /** Si no es {@code null}, se entrega al conectar (como hace el servidor real). */
    String alConectar = INFO;
    boolean enviarFalla;
    private volatile IReceptorServidor receptor;
    private volatile boolean conectada;

    @Override
    public void conectar(String host, int puerto, IReceptorServidor r) {
        receptor = r;
        conectada = true;
        if (alConectar != null) {
            r.alRecibir(alConectar);
        }
    }

    @Override
    public void enviar(String json) {
        if (!conectada || enviarFalla) {
            throw new IllegalStateException("No hay conexión con el servidor");
        }
        enviados.add(json);
    }

    @Override
    public void cerrar() {
        if (conectada) {
            caer("Conexión cerrada por el cliente");
        }
    }

    @Override
    public String getIpServidor() {
        return "10.0.0.5";
    }

    @Override
    public boolean estaConectada() {
        return conectada;
    }

    /** Simula una trama del servidor. */
    void servidor(String json) {
        receptor.alRecibir(json);
    }

    /** Simula la pérdida de la conexión. */
    void caer(String motivo) {
        conectada = false;
        receptor.alDesconectar(motivo);
    }

    String siguienteEnviado() throws InterruptedException {
        return enviados.poll(5, TimeUnit.SECONDS);
    }
}
