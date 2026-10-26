package com.universidad.mensajeria.cliente.inicio;

/** Pantallas del cliente (mockups C1–C3), con su FXML y su tamaño inicial. */
public enum TipoVista {
    LOGIN("/fxml/login.fxml", 420, 560),
    CHAT("/fxml/chat.fxml", 1100, 720),
    INFORMES("/fxml/informes.fxml", 1000, 680);

    private final String fxml;
    private final double ancho;
    private final double alto;

    TipoVista(String fxml, double ancho, double alto) {
        this.fxml = fxml;
        this.ancho = ancho;
        this.alto = alto;
    }

    /** @return la ruta del FXML en el classpath. */
    public String fxml() {
        return fxml;
    }

    public double ancho() {
        return ancho;
    }

    public double alto() {
        return alto;
    }
}
