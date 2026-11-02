package me.esturafd.demo.archivos.areas.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Área de la organización.
 *
 * @param active si admite nuevas cargas y asignaciones; las áreas no se borran
 */
public record Area(
    UUID id,
    String name,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
