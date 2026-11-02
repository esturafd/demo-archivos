package me.esturafd.demo.archivos.areas.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Cuerpo del alta de un área.
 *
 * @param name        nombre único sin distinguir mayúsculas
 * @param description descripción opcional
 */
@Schema(description = "Cuerpo del alta de un área.")
public record CreateAreaRequest(
    @NotBlank @Size(max = 120)
    @Schema(description = "Nombre del área, único sin distinguir mayúsculas.", example = "Área A")
    String name,
    @Size(max = 500)
    @Schema(description = "Descripción del área (opcional).",
        example = "Área de atención al cliente.")
    String description
) {
}
