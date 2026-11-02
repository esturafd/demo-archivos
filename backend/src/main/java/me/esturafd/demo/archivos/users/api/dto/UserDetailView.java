package me.esturafd.demo.archivos.users.api.dto;

import me.esturafd.demo.archivos.users.application.model.UserDetail;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Vista pública de un usuario. Nunca incluye {@code password_hash}.
 */
@Schema(description = "Vista pública de un usuario. Nunca incluye `password_hash`.")
public record UserDetailView(
    @Schema(description = "Identificador del usuario.",
        example = "3f14e45f-ea2e-4f5b-9b2a-1c2d3e4f5a6b")
    String id,
    @Schema(description = "Correo del usuario.", example = "maria@ejemplo.com")
    String email,
    @Schema(description = "Nombre del usuario.", example = "María Pérez")
    String name,
    @Schema(description = "Rol: `standard`, `area_head`, `manager` o `admin`.", example = "manager")
    String role,
    @Schema(description = "Área propia, o ausente si el rol no la requiere.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    String areaId,
    @Schema(description = "Nombre del área propia.", example = "Área A")
    String areaName,
    @Schema(description = "Si la cuenta está activa.", example = "true")
    boolean active,
    @Schema(description = "Momento en que se creó.", example = "2026-01-31T10:15:30Z")
    Instant createdAt
) {

    public static UserDetailView from(UserDetail user) {
        return new UserDetailView(
            user.id().toString(),
            user.email(),
            user.name(),
            user.role(),
            user.areaId() == null ? null : user.areaId().toString(),
            user.areaName(),
            user.active(),
            user.createdAt()
        );
    }
}
