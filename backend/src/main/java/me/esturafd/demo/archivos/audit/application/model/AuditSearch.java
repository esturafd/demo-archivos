package me.esturafd.demo.archivos.audit.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del historial: tipo de acción, actor y posición del cursor.
 *
 * @param action          valor de la acción, o {@code null}
 * @param actorId         usuario que realizó la acción, o {@code null}
 * @param cursorOccurredAt fecha del último evento anterior, o {@code null}
 * @param cursorId        identificador del último evento anterior, o {@code null}
 * @param limit           tamaño de página
 */
public record AuditSearch(
    String action,
    UUID actorId,
    Instant cursorOccurredAt,
    Long cursorId,
    int limit
) {
}
