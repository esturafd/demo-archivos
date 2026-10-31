package me.esturafd.demo.archivos.managedareas.api.dto;

import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;

import java.util.List;

/**
 * Áreas gestionadas por un gerente.
 */
public record ManagedAreasResponse(List<ManagedAreaView> areas) {

    public static ManagedAreasResponse from(List<ManagedArea> areas) {
        return new ManagedAreasResponse(areas.stream().map(ManagedAreaView::from).toList());
    }
}
