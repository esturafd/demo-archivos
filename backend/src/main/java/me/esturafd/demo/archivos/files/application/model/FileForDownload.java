package me.esturafd.demo.archivos.files.application.model;

import java.util.UUID;

/**
 * Datos internos para descargar un archivo: los metadatos más la clave del
 * objeto.
 *
 * <p>Solo circula entre el caso de uso y el adaptador de datos; la clave nunca
 * llega a la respuesta.
 */
public record FileForDownload(
    UUID id,
    String storageKey,
    String originalName,
    String mimeType,
    long sizeBytes
) {
}
