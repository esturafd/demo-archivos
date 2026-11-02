package me.esturafd.demo.archivos.files.application.model;

import java.util.UUID;

/**
 * Datos para registrar un archivo nuevo.
 *
 * <p>El área la decide el servidor: la propia para estándar y jefe de área,
 * {@code null} para gerente y administrador. La política de inserción de la base
 * lo vuelve a comprobar.
 */
public record NewFile(
    UUID id,
    UUID ownerId,
    String ownerEmail,
    UUID areaId,
    String storageKey,
    String originalName,
    String mimeType,
    long sizeBytes,
    String sha256Hex
) {
}
