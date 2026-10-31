package me.esturafd.demo.archivos.areas.api.dto;

import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Cuerpo de la edición de un área. Los campos ausentes o {@code null} se
 * conservan.
 *
 * @param name        nombre nuevo, o {@code null} para conservarlo
 * @param description descripción nueva, o {@code null} para conservarla
 * @param active      estado nuevo, o {@code null} para conservarlo
 */
@Schema(description = "Cuerpo de la edición de un área. Los campos ausentes se conservan.")
public record UpdateAreaRequest(
    @Size(max = 120)
    @Schema(description = "Nombre nuevo, o ausente para conservarlo.", example = "Área A")
    String name,
    @Size(max = 500)
    @Schema(description = "Descripción nueva, o ausente para conservarla.",
        example = "Área de atención al cliente.")
    String description,
    @Schema(description = "Estado nuevo; `false` desactiva el área.", example = "true")
    Boolean active
) {
}
