package me.esturafd.demo.archivos.files.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros del listado: búsqueda por nombre, propietario, posición del cursor y
 * tamaño de página.
 *
 * @param text             texto a buscar en el nombre, o {@code null}
 * @param ownerId          propietario por el que filtrar, o {@code null} para todos los visibles
 * @param cursorUploadedAt fecha del último elemento de la página anterior, o {@code null}
 * @param cursorId         identificador del último elemento anterior, o {@code null}
 * @param limit            tamaño de página solicitado
 */
public record FileSearch(
    String text,
    UUID ownerId,
    Instant cursorUploadedAt,
    UUID cursorId,
    int limit
) {
}
