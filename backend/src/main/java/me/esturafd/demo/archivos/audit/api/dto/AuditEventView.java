package me.esturafd.demo.archivos.audit.api.dto;

import me.esturafd.demo.archivos.audit.application.model.AuditEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Vista pública de un evento del historial.
 */
public record AuditEventView(
    long id,
    Instant occurredAt,
    String action,
    String actorUserId,
    String actorEmail,
    String actorRole,
    String actorAreaName,
    String fileId,
    String fileName,
    String ipAddress,
    String requestId,
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
