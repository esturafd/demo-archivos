package me.esturafd.demo.archivos.managedareas.api.dto;

import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;

/**
 * Vista pública de un área gestionada.
 */
public record ManagedAreaView(String id, String name, boolean active) {

    public static ManagedAreaView from(ManagedArea area) {
        return new ManagedAreaView(area.id().toString(), area.name(), area.active());
    }
}
