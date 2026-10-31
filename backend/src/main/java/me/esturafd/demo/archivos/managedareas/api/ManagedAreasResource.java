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
import me.esturafd.demo.archivos.shared.security.api.RequiresAdmin;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Subrecurso de las áreas gestionadas por un gerente.
 *
 * <p>El administrador ve y reemplaza las de cualquier gerente. Un gerente solo
 * consulta las suyas (RLS); el reemplazo está reservado al administrador, así
 * que un gerente no puede asignarse áreas a sí mismo.
 */
@Path("/api/users/{id}/managed-areas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresSession
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
    @RequiresAdmin
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
