package me.esturafd.demo.archivos.managedareas.api.dto;

import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Áreas gestionadas por un gerente.
 */
@Schema(description = "Áreas gestionadas por un gerente.")
public record ManagedAreasResponse(
    @Schema(description = "Áreas que gestiona el gerente.")
    List<ManagedAreaView> areas
) {

    public static ManagedAreasResponse from(List<ManagedArea> areas) {
        return new ManagedAreasResponse(areas.stream().map(ManagedAreaView::from).toList());
    }
}
