package me.esturafd.demo.archivos.files.api.dto;

import me.esturafd.demo.archivos.files.application.model.StoredFile;

import java.time.Instant;

/**
 * Vista pública de un archivo.
 *
 * <p>No incluye la {@code storage_key} ni ninguna ruta física: el contenido solo
 * se obtiene descargándolo por la API.
 */
public record FileView(
    String id,
    String originalName,
    String mimeType,
    long sizeBytes,
    String ownerEmail,
    String areaId,
    Instant uploadedAt
) {

    public static FileView from(StoredFile file) {
        return new FileView(
            file.id().toString(),
            file.originalName(),
            file.mimeType(),
            file.sizeBytes(),
            file.ownerEmail(),
            file.areaId() == null ? null : file.areaId().toString(),
            file.uploadedAt()
        );
    }
}
