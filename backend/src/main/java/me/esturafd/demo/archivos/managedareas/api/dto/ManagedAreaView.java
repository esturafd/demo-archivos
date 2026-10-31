package me.esturafd.demo.archivos.managedareas.api.dto;

import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Vista pública de un área gestionada.
 */
@Schema(description = "Vista pública de un área gestionada.")
public record ManagedAreaView(
    @Schema(description = "Identificador del área.",
        example = "5c2a1b0e-1111-2222-3333-444455556666")
    String id,
    @Schema(description = "Nombre del área.", example = "Área A")
    String name,
    @Schema(description = "Si el área está activa.", example = "true")
    boolean active
) {

    public static ManagedAreaView from(ManagedArea area) {
        return new ManagedAreaView(area.id().toString(), area.name(), area.active());
    }
}
