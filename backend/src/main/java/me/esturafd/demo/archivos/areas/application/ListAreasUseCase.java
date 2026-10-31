package me.esturafd.demo.archivos.areas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;

/**
 * Caso de uso del listado de áreas.
 */
@ApplicationScoped
public class ListAreasUseCase {

    @Inject
    AreasDataPort data;

    public List<Area> list(SessionUser actor, String requestId, String ipAddress) {
        return data.listVisibleAreas(actor.id(), requestId, ipAddress);
    }
}
