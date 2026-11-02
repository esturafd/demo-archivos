package me.esturafd.demo.archivos.areas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

/**
 * Caso de uso del alta de un área.
 *
 * <p>Solo llega aquí un administrador (lo garantiza la guarda de la ruta). Un
 * nombre repetido sin distinguir mayúsculas lo rechaza el índice único de la
 * base y se traduce a {@code 409}.
 */
@ApplicationScoped
public class CreateAreaUseCase {

    @Inject
    AreasDataPort data;

    public Area create(
        SessionUser actor,
        String name,
        String description,
        String requestId,
        String ipAddress
    ) {
        return data.createArea(actor.id(), name, description, requestId, ipAddress);
    }
}
