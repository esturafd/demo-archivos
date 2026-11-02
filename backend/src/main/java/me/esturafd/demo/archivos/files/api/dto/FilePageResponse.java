package me.esturafd.demo.archivos.files.api.dto;

import me.esturafd.demo.archivos.files.application.model.FilePage;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Página del listado de archivos.
 *
 * @param items      archivos de esta página
 * @param nextCursor cursor opaco para la siguiente, o {@code null} si no hay más
 */
@Schema(description = "Página del listado de archivos, paginada por cursor.")
public record FilePageResponse(
    @Schema(description = "Archivos de esta página.")
    List<FileView> items,
    @Schema(description = "Cursor opaco para la página siguiente, o ausente si no hay más.",
        example = "MTc2OTg0MjUzMDAwMHw0Mg")
    String nextCursor
) {

    public static FilePageResponse from(FilePage page) {
        return new FilePageResponse(
            page.items().stream().map(FileView::from).toList(),
            page.nextCursor()
        );
    }
}
