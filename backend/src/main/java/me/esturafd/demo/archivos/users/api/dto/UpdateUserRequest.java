package me.esturafd.demo.archivos.users.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Cuerpo de la edición de un usuario. Los campos ausentes o {@code null} se
 * conservan.
 *
 * @param role   rol nuevo, o {@code null} para conservarlo
 * @param active estado nuevo, o {@code null} para conservarlo
 */
@Schema(description = "Cuerpo de la edición de un usuario. Los campos ausentes se conservan.")
public record UpdateUserRequest(
    @Size(max = 120)
    @Schema(description = "Nombre nuevo, o ausente para conservarlo.", example = "María Pérez")
    String name,
    @Pattern(regexp = "standard|area_head|manager|admin")
    @Schema(description = "Rol nuevo, o ausente para conservarlo.", example = "manager",
        enumeration = {"standard", "area_head", "manager", "admin"})
    String role,
    @Schema(description = "Área nueva, o ausente para conservarla.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    UUID areaId,
    @Schema(description = "Estado nuevo; `false` desactiva y revoca las sesiones abiertas.",
        example = "true")
    Boolean active
) {
}
