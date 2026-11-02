package me.esturafd.demo.archivos.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Cuerpo de la petición de inicio de sesión.
 *
 * @param email    correo
 * @param password contraseña en claro, solo durante el transporte por HTTPS
 */
@Schema(description = "Credenciales de inicio de sesión.")
public record LoginRequest(
    @NotBlank @Email @Size(max = 320)
    @Schema(description = "Correo del usuario.", example = "maria@ejemplo.com")
    String email,
    @NotBlank @Size(min = 1, max = 200)
    @Schema(description = "Contraseña en claro, solo durante el transporte por HTTPS.",
        example = "ContraseñaSegura123")
    String password
) {
}
