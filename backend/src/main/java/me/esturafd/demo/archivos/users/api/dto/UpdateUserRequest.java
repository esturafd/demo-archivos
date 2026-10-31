package me.esturafd.demo.archivos.users.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cuerpo de la edición de un usuario. Los campos ausentes o {@code null} se
 * conservan.
 *
 * @param role   rol nuevo, o {@code null} para conservarlo
 * @param active estado nuevo, o {@code null} para conservarlo
 */
public record UpdateUserRequest(
    @Size(max = 120) String name,
    @Pattern(regexp = "standard|area_head|manager|admin") String role,
    UUID areaId,
    Boolean active
) {
}
