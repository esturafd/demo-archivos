package me.esturafd.demo.archivos.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.data.application.DataMediator;
import me.esturafd.demo.archivos.shared.data.application.model.SessionSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas sobre las sesiones activas de un usuario.
 */
@ApplicationScoped
public class QuerySessions {

    @Inject
    DataMediator data;

    public List<SessionSummary> list(UUID userId, String requestId, String ipAddress) {
        return data.listUserSessions(userId, requestId, ipAddress);
    }

    public Optional<SessionSummary> current(
        UUID userId,
        UUID sessionId,
        String requestId,
        String ipAddress
    ) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return list(userId, requestId, ipAddress).stream()
            .filter(session -> session.id().equals(sessionId))
            .findFirst();
    }
}
