package me.esturafd.demo.archivos.auth.api.dto;

import me.esturafd.demo.archivos.auth.application.model.SessionSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista pública de una sesión.
 *
 * @param current si es la sesión con la que se está navegando
 */
@Schema(description = "Vista pública de una sesión.")
public record SessionView(
    @Schema(description = "Identificador de la sesión.",
        example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    String id,
    @Schema(description = "Momento en que se creó.", example = "2026-01-31T10:15:30Z")
    Instant createdAt,
    @Schema(description = "Momento en que expira.", example = "2026-01-31T22:15:30Z")
    Instant expiresAt,
    @Schema(description = "Última vez que se usó.", example = "2026-01-31T12:00:00Z")
    Instant lastSeenAt,
    @Schema(description = "IP desde la que se abrió.", example = "203.0.113.10")
    String ipAddress,
    @Schema(description = "Agente de usuario que la abrió.", example = "Mozilla/5.0")
    String userAgent,
    @Schema(description = "Si es la sesión con la que se navega.", example = "true")
    boolean current
) {

    public static SessionView from(SessionSummary session, UUID currentSessionId) {
        return new SessionView(
            session.id().toString(),
            session.createdAt(),
            session.expiresAt(),
            session.lastSeenAt(),
            session.ipAddress(),
            session.userAgent(),
            currentSessionId != null && session.id().equals(currentSessionId)
        );
    }
}
