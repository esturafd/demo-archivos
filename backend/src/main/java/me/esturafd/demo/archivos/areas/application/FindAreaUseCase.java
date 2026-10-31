package me.esturafd.demo.archivos.areas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Caso de uso de la consulta de un área.
 */
@ApplicationScoped
public class FindAreaUseCase {

    @Inject
    AreasDataPort data;

    public Area find(UUID areaId, SessionUser actor, String requestId, String ipAddress) {
        return data.findVisibleArea(areaId, actor.id(), requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }
}
