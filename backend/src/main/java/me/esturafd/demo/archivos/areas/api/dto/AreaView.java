package me.esturafd.demo.archivos.areas.api.dto;

import me.esturafd.demo.archivos.areas.application.model.Area;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Vista pública de un área.
 */
@Schema(description = "Vista pública de un área.")
public record AreaView(
    @Schema(description = "Identificador del área.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    String id,
    @Schema(description = "Nombre del área, único sin distinguir mayúsculas.", example = "Área A")
    String name,
    @Schema(description = "Descripción del área.", example = "Área de atención al cliente.")
    String description,
    @Schema(description = "Si el área está activa.", example = "true")
    boolean active,
    @Schema(description = "Momento en que se creó.", example = "2026-01-31T10:15:30Z")
    Instant createdAt,
    @Schema(description = "Momento de la última edición.", example = "2026-01-31T11:00:00Z")
    Instant updatedAt
) {

    public static AreaView from(Area area) {
        return new AreaView(
            area.id().toString(),
            area.name(),
            area.description(),
            area.active(),
            area.createdAt(),
            area.updatedAt()
        );
    }
}
