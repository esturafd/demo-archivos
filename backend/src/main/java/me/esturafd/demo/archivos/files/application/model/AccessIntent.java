package me.esturafd.demo.archivos.files.application.model;

/**
 * Intención de un acceso denegado, para el historial.
 */
public enum AccessIntent {

    DOWNLOAD,
    DELETE;

    /** Valor que viaja en el historial ({@code metadata.intento}). */
    public String metadataValue() {
        return name().toLowerCase();
    }
}
