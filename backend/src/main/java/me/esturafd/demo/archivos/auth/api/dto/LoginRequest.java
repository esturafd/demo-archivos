package me.esturafd.demo.archivos.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición de inicio de sesión.
 *
 * @param email    correo
 * @param password contraseña en claro, solo durante el transporte por HTTPS
 */
public record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 1, max = 200) String password
) {
}
