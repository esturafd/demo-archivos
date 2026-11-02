package me.esturafd.demo.archivos.managedareas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de la consulta de las áreas gestionadas por un gerente.
 */
@ApplicationScoped
public class ListManagedAreasUseCase {

    @Inject
    ManagedAreasDataPort data;

    public List<ManagedArea> list(
        UUID managerId,
        SessionUser actor,
        String requestId,
        String ipAddress
    ) {
        return data.listManagedAreas(managerId, actor.id(), requestId, ipAddress);
    }
}
