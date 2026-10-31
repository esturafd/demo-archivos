package me.esturafd.demo.archivos.areas.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la edición de un área. Los campos ausentes o {@code null} se
 * conservan.
 *
 * @param name        nombre nuevo, o {@code null} para conservarlo
 * @param description descripción nueva, o {@code null} para conservarla
 * @param active      estado nuevo, o {@code null} para conservarlo
 */
public record UpdateAreaRequest(
    @Size(max = 120) String name,
    @Size(max = 500) String description,
    Boolean active
) {
}
