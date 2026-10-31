package me.esturafd.demo.archivos.managedareas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso del reemplazo del conjunto de áreas gestionadas.
 *
 * <p>Un conjunto vacío se rechaza: un gerente debe conservar al menos un área.
 * El reemplazo lo hace el adaptador en una sola transacción.
 */
@ApplicationScoped
public class ReplaceManagedAreasUseCase {

    @Inject
    ManagedAreasDataPort data;

    public List<ManagedArea> replace(
        UUID managerId,
        SessionUser actor,
        List<UUID> areaIds,
        String requestId,
        String ipAddress
    ) {
        if (areaIds == null || areaIds.isEmpty()) {
            throw ApiException.badRequest("Un gerente debe conservar al menos un área.");
        }
        return data.replaceManagedAreas(managerId, actor.id(), areaIds, requestId, ipAddress);
    }
}
