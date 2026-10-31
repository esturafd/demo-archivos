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
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirementsSet;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
@Tag(name = "Áreas")
@APIResponses({
    @APIResponse(
        responseCode = "401",
        description = "No hay una sesión válida.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "500",
        description = "Error inesperado.",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
})
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
    @Operation(
        summary = "Listar áreas",
        description = "Áreas visibles para el usuario, ordenadas por nombre. RLS acota el alcance."
    )
    @APIResponse(responseCode = "200", description = "Áreas visibles.")
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
    @Operation(
        summary = "Crear un área",
        description = "Da de alta un área. El nombre es único sin distinguir mayúsculas."
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Área creada.",
            headers = @Header(
                name = "Location",
                description = "Ruta del área creada, `/api/areas/{id}`."),
            content = @Content(schema = @Schema(implementation = AreaView.class))),
        @APIResponse(
            responseCode = "400",
            description = "El cuerpo no supera la validación.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "403",
            description = "La sesión no tiene nivel de administrador.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "409",
            description = "Ya existe un área con ese nombre.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(summary = "Consultar un área", description = "Un área por su identificador.")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Datos del área.",
            content = @Content(schema = @Schema(implementation = AreaView.class))),
        @APIResponse(
            responseCode = "404",
            description = "El área no existe o queda fuera del alcance.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AreaView find(
        @Parameter(description = "Identificador del área.", required = true) @PathParam("id") UUID id
    ) {
        SessionUser actor = currentSession.require();
        return AreaView.from(findArea.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Edita o desactiva un área. Solo el administrador. */
    @PATCH
    @Path("/{id}")
    @RequiresRole(AppRole.ADMIN)
    @Operation(
        summary = "Editar un área",
        description = "Cambia nombre, descripción o estado. Los campos ausentes se conservan."
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Área actualizada.",
            content = @Content(schema = @Schema(implementation = AreaView.class))),
        @APIResponse(
            responseCode = "400",
            description = "El cuerpo no supera la validación.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "403",
            description = "La sesión no tiene nivel de administrador.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "404",
            description = "El área no existe.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "409",
            description = "Ya existe otra área con ese nombre.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AreaView update(
        @Parameter(description = "Identificador del área.", required = true) @PathParam("id") UUID id,
        @Valid UpdateAreaRequest request
    ) {
        SessionUser actor = currentSession.require();
        Area area = updateArea.update(
            id, actor, request.name(), request.description(), request.active(),
            context.requestId(), context.clientIp());
        return AreaView.from(area);
    }
}
