package me.esturafd.demo.archivos.audit.api.dto;

import me.esturafd.demo.archivos.audit.application.model.AuditEvent;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Vista pública de un evento del historial.
 */
@Schema(description = "Un evento del historial. Conserva copias de los datos al ejecutarse la acción.")
public record AuditEventView(
    @Schema(description = "Identificador del evento.", example = "42")
    long id,
    @Schema(description = "Momento en que ocurrió.", example = "2026-01-31T10:15:30Z")
    Instant occurredAt,
    @Schema(description = "Acción registrada.", example = "file_uploaded")
    String action,
    @Schema(description = "Usuario que la realizó.",
        example = "3f14e45f-ea2e-4f5b-9b2a-1c2d3e4f5a6b")
    String actorUserId,
    @Schema(description = "Correo del usuario al ejecutar la acción.", example = "maria@ejemplo.com")
    String actorEmail,
    @Schema(description = "Rol del usuario al ejecutar la acción.", example = "standard")
    String actorRole,
    @Schema(description = "Nombre del área del usuario al ejecutar la acción.", example = "Área A")
    String actorAreaName,
    @Schema(description = "Archivo implicado, si aplica.",
        example = "7d4c3b2a-1111-2222-3333-444455556666")
    String fileId,
    @Schema(description = "Nombre del archivo al ejecutar la acción.", example = "informe.pdf")
    String fileName,
    @Schema(description = "IP desde la que se ejecutó.", example = "203.0.113.10")
    String ipAddress,
    @Schema(description = "Identificador de la petición.", example = "a1b2c3d4")
    String requestId,
    @Schema(description = "Metadatos adicionales del evento.", example = "{\"size\":20480}")
    Map<String, Object> metadata
) {

    public static AuditEventView from(AuditEvent event) {
        return new AuditEventView(
            event.id(),
            event.occurredAt(),
            event.action(),
            event.actorUserId() == null ? null : event.actorUserId().toString(),
            event.actorEmail(),
            event.actorRole(),
            event.actorAreaName(),
            event.fileId() == null ? null : event.fileId().toString(),
            event.fileName(),
            event.ipAddress(),
            event.requestId(),
            event.metadata()
        );
    }
}
