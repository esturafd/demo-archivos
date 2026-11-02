package me.esturafd.demo.archivos.audit.api.dto;

import me.esturafd.demo.archivos.audit.application.model.AuditPage;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Página del historial.
 */
@Schema(description = "Página del historial, paginada por cursor.")
public record AuditPageResponse(
    @Schema(description = "Eventos de esta página.")
    List<AuditEventView> items,
    @Schema(description = "Cursor opaco para la página siguiente, o ausente si no hay más.",
        example = "MTc2OTg0MjUzMDAwMHw0Mg")
    String nextCursor
) {

    public static AuditPageResponse from(AuditPage page) {
        return new AuditPageResponse(
            page.items().stream().map(AuditEventView::from).toList(),
            page.nextCursor()
        );
    }
}
