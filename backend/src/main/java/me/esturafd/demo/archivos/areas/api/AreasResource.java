package me.esturafd.demo.archivos.areas.api;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.esturafd.demo.archivos.areas.api.dto.AreaView;
import me.esturafd.demo.archivos.areas.api.dto.CreateAreaRequest;
import me.esturafd.demo.archivos.areas.api.dto.UpdateAreaRequest;
import me.esturafd.demo.archivos.areas.application.CreateAreaUseCase;
import me.esturafd.demo.archivos.areas.application.FindAreaUseCase;
import me.esturafd.demo.archivos.areas.application.ListAreasUseCase;
import me.esturafd.demo.archivos.areas.application.UpdateAreaUseCase;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;
import java.util.UUID;

/**
 * Recurso de áreas.
 *
 * <p>Leer exige sesión y RLS acota el alcance. Escribir está reservado al
 * administrador con {@code @RequiresRole(AppRole.ADMIN)}; la base lo vuelve a comprobar con
 * sus políticas. No hay {@code DELETE}: las áreas se desactivan.
 */
@Path("/api/areas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresSession
public class AreasResource {

    @Inject
    ListAreasUseCase listAreas;

    @Inject
    FindAreaUseCase findArea;

    @Inject
    CreateAreaUseCase createArea;

    @Inject
    UpdateAreaUseCase updateArea;

    @Inject
    CurrentSession currentSession;

    @Inject
    RequestContext context;

    /** Áreas visibles para el usuario, ordenadas por nombre. */
    @GET
    public List<AreaView> list() {
        SessionUser actor = currentSession.require();
        return listAreas.list(actor, context.requestId(), context.clientIp())
            .stream()
            .map(AreaView::from)
            .toList();
    }

    /** Crea un área. Solo el administrador. */
    @POST
    @RequiresRole(AppRole.ADMIN)
    public Response create(@Valid CreateAreaRequest request) {
        SessionUser actor = currentSession.require();
        Area area = createArea.create(
            actor, request.name(), request.description(), context.requestId(), context.clientIp());

        return Response.status(Response.Status.CREATED)
            .header(HttpHeaders.LOCATION, "/api/areas/" + area.id())
            .entity(AreaView.from(area))
            .build();
    }

    /** Área visible por su identificador. */
    @GET
    @Path("/{id}")
    public AreaView find(@PathParam("id") UUID id) {
        SessionUser actor = currentSession.require();
        return AreaView.from(findArea.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Edita o desactiva un área. Solo el administrador. */
    @PATCH
    @Path("/{id}")
    @RequiresRole(AppRole.ADMIN)
    public AreaView update(@PathParam("id") UUID id, @Valid UpdateAreaRequest request) {
        SessionUser actor = currentSession.require();
        Area area = updateArea.update(
            id, actor, request.name(), request.description(), request.active(),
            context.requestId(), context.clientIp());
        return AreaView.from(area);
    }
}
