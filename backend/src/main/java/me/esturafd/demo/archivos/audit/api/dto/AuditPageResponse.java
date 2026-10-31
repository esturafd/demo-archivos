package me.esturafd.demo.archivos.audit.api.dto;

import me.esturafd.demo.archivos.audit.application.model.AuditPage;

import java.util.List;

/**
 * Página del historial.
 */
public record AuditPageResponse(List<AuditEventView> items, String nextCursor) {

    public static AuditPageResponse from(AuditPage page) {
        return new AuditPageResponse(
            page.items().stream().map(AuditEventView::from).toList(),
            page.nextCursor()
        );
    }
}
