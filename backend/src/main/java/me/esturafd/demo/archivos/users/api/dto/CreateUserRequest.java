package me.esturafd.demo.archivos.users.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cuerpo del alta de un usuario.
 *
 * @param password contraseña en claro, solo durante el transporte por HTTPS
 * @param role     uno de {@code standard}, {@code area_head}, {@code manager}, {@code admin}
 * @param areaId   área propia; obligatoria para estándar y jefe de área
 */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 200) String password,
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Pattern(regexp = "standard|area_head|manager|admin") String role,
    UUID areaId
) {
}
