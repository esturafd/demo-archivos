package me.esturafd.demo.archivos.managedareas.api;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import me.esturafd.demo.archivos.managedareas.api.dto.ManagedAreasRequest;
import me.esturafd.demo.archivos.managedareas.api.dto.ManagedAreasResponse;
import me.esturafd.demo.archivos.managedareas.application.ListManagedAreasUseCase;
import me.esturafd.demo.archivos.managedareas.application.ReplaceManagedAreasUseCase;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Subrecurso de las áreas gestionadas por un gerente.
 *
 * <p>Consultar exige nivel **manager o superior**: el administrador ve las de
 * cualquier gerente y un gerente solo las suyas (RLS). Reemplazar está reservado
 * al administrador, así que un gerente no puede asignarse áreas a sí mismo. Un
 * estándar o jefe de área recibe {@code 403} antes de llegar a la base.
 */
@Path("/api/users/{id}/managed-areas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresSession
@RequiresRole(AppRole.MANAGER)
public class ManagedAreasResource {

    @Inject
    ListManagedAreasUseCase listManagedAreas;

    @Inject
    ReplaceManagedAreasUseCase replaceManagedAreas;

    @Inject
    CurrentSession currentSession;

    @Inject
    RequestContext context;

    /** Áreas que gestiona el gerente. */
    @GET
    public ManagedAreasResponse list(@PathParam("id") UUID managerId) {
        SessionUser actor = currentSession.require();
        return ManagedAreasResponse.from(
            listManagedAreas.list(managerId, actor, context.requestId(), context.clientIp()));
    }

    /** Reemplaza el conjunto completo. Solo el administrador. */
    @PUT
    @RequiresRole(AppRole.ADMIN)
    public ManagedAreasResponse replace(
        @PathParam("id") UUID managerId,
        @Valid ManagedAreasRequest request
    ) {
        SessionUser actor = currentSession.require();
        return ManagedAreasResponse.from(
            replaceManagedAreas.replace(
                managerId, actor, request.areaIds(), context.requestId(), context.clientIp()));
    }
}
