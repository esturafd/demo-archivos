package me.esturafd.demo.archivos.audit.application.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Un evento del historial.
 *
 * <p>Las referencias al usuario y al archivo son **copias** tomadas al ejecutar la
 * acción, no enlaces vivos: el historial sigue siendo legible aunque después
 * cambie el área del usuario o se elimine el archivo.
 */
public record AuditEvent(
    long id,
    Instant occurredAt,
    String action,
    UUID actorUserId,
    String actorEmail,
    String actorRole,
    String actorAreaName,
    UUID fileId,
    String fileName,
    String ipAddress,
    String requestId,
    Map<String, Object> metadata
) {
}
