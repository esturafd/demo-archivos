package me.esturafd.demo.archivos.files.application.model;

import java.io.InputStream;

/**
 * Contenido listo para transmitir al cliente, con los datos de su cabecera.
 *
 * <p>El flujo lo cierra quien lo consume (la capa de API).
 */
public record DownloadedFile(
    String originalName,
    String mimeType,
    long sizeBytes,
    InputStream content
) {
}
