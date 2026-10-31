package me.esturafd.demo.archivos.files.application.model;

import java.util.List;

/**
 * Página del listado.
 *
 * @param items      archivos de esta página
 * @param nextCursor cursor para la siguiente, o {@code null} si no hay más
 */
public record FilePage(List<StoredFile> items, String nextCursor) {
}
