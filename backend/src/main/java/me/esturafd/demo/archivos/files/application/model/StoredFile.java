package me.esturafd.demo.archivos.files.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadatos visibles de un archivo.
 *
 * <p>No incluye la {@code storage_key}: es interna y nunca sale del adaptador.
 */
public record StoredFile(
    UUID id,
    String originalName,
    String mimeType,
    long sizeBytes,
    UUID ownerId,
    String ownerEmail,
    UUID areaId,
    Instant uploadedAt
) {
}
