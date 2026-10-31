package me.esturafd.demo.archivos.managedareas.api.dto;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo del reemplazo del conjunto de áreas gestionadas.
 *
 * @param areaIds conjunto completo; no puede quedar vacío
 */
@Schema(description = "Conjunto completo de áreas gestionadas. Reemplaza al anterior.")
public record ManagedAreasRequest(
    @NotNull
    @Schema(
        description = "Áreas que gestionará el gerente. No puede quedar vacío.",
        example = "[\"5c2a1b0e-1111-2222-3333-444455556666\"]")
    List<UUID> areaIds
) {
}
