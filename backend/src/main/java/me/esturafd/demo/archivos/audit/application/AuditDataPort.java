package me.esturafd.demo.archivos.audit.application;

import me.esturafd.demo.archivos.audit.application.model.AuditEvent;
import me.esturafd.demo.archivos.audit.application.model.AuditSearch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de datos del módulo `audit`.
 *
 * <p>Es de solo lectura. El alcance lo aplica RLS con
 * {@code app.can_view_history}: administrador todo, gerente sus acciones y las
 * de sus áreas, estándar y jefe de área ninguna.
 */
public interface AuditDataPort {

    /** Eventos visibles para el actor, con filtros y cursor. Devuelve hasta {@code limit + 1}. */
    List<AuditEvent> listVisibleEvents(
        AuditSearch search,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /** Evento visible por su identificador, o vacío si no existe o queda fuera de alcance. */
    Optional<AuditEvent> findVisibleEvent(
        long eventId,
        UUID actorId,
        String requestId,
        String ipAddress
    );
}
