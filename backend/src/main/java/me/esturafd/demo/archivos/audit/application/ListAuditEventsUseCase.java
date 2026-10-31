package me.esturafd.demo.archivos.audit.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.audit.application.model.AuditCursor;
import me.esturafd.demo.archivos.audit.application.model.AuditEvent;
import me.esturafd.demo.archivos.audit.application.model.AuditPage;
import me.esturafd.demo.archivos.audit.application.model.AuditSearch;
import me.esturafd.demo.archivos.audit.domain.AuditAction;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso del listado del historial.
 *
 * <p>Aplica los filtros de acción y actor y pagina por cursor. Un valor de acción
 * desconocido se rechaza con {@code 400}.
 */
@ApplicationScoped
public class ListAuditEventsUseCase {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    @Inject
    AuditDataPort data;

    public AuditPage list(
        SessionUser actor,
        String action,
        UUID actorFilter,
        String cursor,
        Integer limit,
        String requestId,
        String ipAddress
    ) {
        String actionFilter = action == null || action.isBlank()
            ? null
            : AuditAction.from(action)
                .orElseThrow(() -> ApiException.badRequest("Acción de historial desconocida."))
                .value();

        int pageSize = pageSize(limit);
        Optional<AuditCursor> position = AuditCursor.decode(cursor);

        AuditSearch search = new AuditSearch(
            actionFilter,
            actorFilter,
            position.map(AuditCursor::occurredAt).orElse(null),
            position.map(AuditCursor::id).orElse(null),
            pageSize
        );

        List<AuditEvent> rows = data.listVisibleEvents(search, actor.id(), requestId, ipAddress);
        boolean hasMore = rows.size() > pageSize;
        List<AuditEvent> items = hasMore ? List.copyOf(rows.subList(0, pageSize)) : rows;

        String nextCursor = hasMore && !items.isEmpty()
            ? AuditCursor.encode(items.get(items.size() - 1).occurredAt(), items.get(items.size() - 1).id())
            : null;

        return new AuditPage(items, nextCursor);
    }

    private static int pageSize(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
