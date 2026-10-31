package me.esturafd.demo.archivos.audit.application.model;

import java.util.List;

/**
 * Página del historial.
 */
public record AuditPage(List<AuditEvent> items, String nextCursor) {
}
