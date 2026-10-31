package me.esturafd.demo.archivos.areas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de un área.
 *
 * @param name        nombre único sin distinguir mayúsculas
 * @param description descripción opcional
 */
public record CreateAreaRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description
) {
}
