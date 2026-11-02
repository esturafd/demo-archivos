package me.esturafd.demo.archivos.audit.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.audit.application.model.AuditEvent;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

/**
 * Caso de uso de la consulta de un evento del historial.
 */
@ApplicationScoped
public class FindAuditEventUseCase {

    @Inject
    AuditDataPort data;

    public AuditEvent find(long eventId, SessionUser actor, String requestId, String ipAddress) {
        return data.findVisibleEvent(eventId, actor.id(), requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }
}
