package me.esturafd.demo.archivos.areas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Caso de uso de la edición y desactivación de un área.
 *
 * <p>Desactivar no borra nada: conserva archivos e historial, y solo impide
 * nuevas cargas. Si el área no existe o no es visible, responde {@code 404}.
 */
@ApplicationScoped
public class UpdateAreaUseCase {

    @Inject
    AreasDataPort data;

    public Area update(
        UUID areaId,
        SessionUser actor,
        String name,
        String description,
        Boolean active,
        String requestId,
        String ipAddress
    ) {
        return data.updateArea(actor.id(), areaId, name, description, active, requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }
}
