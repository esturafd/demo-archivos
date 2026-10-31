package me.esturafd.demo.archivos.auth.api.dto;

import me.esturafd.demo.archivos.shared.data.application.model.SessionSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista pública de una sesión.
 *
 * @param current si es la sesión con la que se está navegando
 */
public record SessionView(
    String id,
    Instant createdAt,
    Instant expiresAt,
    Instant lastSeenAt,
    String ipAddress,
    String userAgent,
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
