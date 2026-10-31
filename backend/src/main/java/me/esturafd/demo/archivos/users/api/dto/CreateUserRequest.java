package me.esturafd.demo.archivos.users.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Cuerpo del alta de un usuario.
 *
 * @param password contraseña en claro, solo durante el transporte por HTTPS
 * @param role     uno de {@code standard}, {@code area_head}, {@code manager}, {@code admin}
 * @param areaId   área propia; obligatoria para estándar y jefe de área
 */
@Schema(description = "Cuerpo del alta de un usuario.")
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 320)
    @Schema(description = "Correo del usuario.", example = "maria@ejemplo.com")
    String email,
    @NotBlank @Size(min = 8, max = 200)
    @Schema(description = "Contraseña en claro, solo durante el transporte por HTTPS.",
        example = "ContraseñaSegura123")
    String password,
    @NotBlank @Size(max = 120)
    @Schema(description = "Nombre del usuario.", example = "María Pérez")
    String name,
    @NotBlank @Pattern(regexp = "standard|area_head|manager|admin")
    @Schema(description = "Rol del usuario.", example = "manager",
        enumeration = {"standard", "area_head", "manager", "admin"})
    String role,
    @Schema(description = "Área propia; obligatoria para estándar y jefe de área.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    UUID areaId
) {
}
