package me.esturafd.demo.archivos.files.api.dto;

import me.esturafd.demo.archivos.files.application.model.StoredFile;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Vista pública de un archivo.
 *
 * <p>No incluye la {@code storage_key} ni ninguna ruta física: el contenido solo
 * se obtiene descargándolo por la API.
 */
@Schema(description = "Vista pública de un archivo. Nunca incluye la clave interna.")
public record FileView(
    @Schema(description = "Identificador del archivo.",
        example = "7d4c3b2a-1111-2222-3333-444455556666")
    String id,
    @Schema(description = "Nombre original del archivo.", example = "informe.pdf")
    String originalName,
    @Schema(description = "Tipo MIME detectado por la firma del contenido.",
        example = "application/pdf")
    String mimeType,
    @Schema(description = "Tamaño en bytes.", example = "20480")
    long sizeBytes,
    @Schema(description = "Correo del propietario.", example = "maria@ejemplo.com")
    String ownerEmail,
    @Schema(description = "Área del propietario al cargarlo, o ausente.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    String areaId,
    @Schema(description = "Momento en que se cargó.", example = "2026-01-31T10:15:30Z")
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
