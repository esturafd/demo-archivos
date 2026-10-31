package me.esturafd.demo.archivos.areas.api.dto;

import me.esturafd.demo.archivos.areas.application.model.Area;

import java.time.Instant;

/**
 * Vista pública de un área.
 */
public record AreaView(
    String id,
    String name,
    String description,
    boolean active,
    Instant createdAt,
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
