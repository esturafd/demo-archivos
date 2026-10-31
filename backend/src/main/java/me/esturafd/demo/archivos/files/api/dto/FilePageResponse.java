package me.esturafd.demo.archivos.files.api.dto;

import me.esturafd.demo.archivos.files.application.model.FilePage;

import java.util.List;

/**
 * Página del listado de archivos.
 *
 * @param items      archivos de esta página
 * @param nextCursor cursor opaco para la siguiente, o {@code null} si no hay más
 */
public record FilePageResponse(List<FileView> items, String nextCursor) {

    public static FilePageResponse from(FilePage page) {
        return new FilePageResponse(
            page.items().stream().map(FileView::from).toList(),
            page.nextCursor()
        );
    }
}
