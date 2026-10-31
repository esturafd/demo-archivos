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
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirementsSet;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
@Tag(name = "Áreas gestionadas")
@APIResponses({
    @APIResponse(
        responseCode = "401",
        description = "No hay una sesión válida.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "403",
        description = "La sesión no alcanza el nivel exigido.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "500",
        description = "Error inesperado.",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
})
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
    @Operation(
        summary = "Consultar las áreas gestionadas",
        description = """
            Áreas que gestiona un gerente. El administrador ve las de cualquier
            gerente; un gerente, solo las suyas (RLS).
            """
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Áreas gestionadas por el gerente.",
            content = @Content(schema = @Schema(implementation = ManagedAreasResponse.class))),
        @APIResponse(
            responseCode = "404",
            description = "El usuario no existe o no es un gerente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ManagedAreasResponse list(
        @Parameter(description = "Identificador del gerente.", required = true) @PathParam("id") UUID managerId
    ) {
        SessionUser actor = currentSession.require();
        return ManagedAreasResponse.from(
            listManagedAreas.list(managerId, actor, context.requestId(), context.clientIp()));
    }

    /** Reemplaza el conjunto completo. Solo el administrador. */
    @PUT
    @RequiresRole(AppRole.ADMIN)
    @Operation(
        summary = "Reemplazar las áreas gestionadas",
        description = """
            Sustituye el conjunto completo de áreas de un gerente en una sola
            transacción. Un conjunto vacío se rechaza.
            """
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Conjunto reemplazado.",
            content = @Content(schema = @Schema(implementation = ManagedAreasResponse.class))),
        @APIResponse(
            responseCode = "400",
            description = "El conjunto está vacío o algún área no es válida.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "404",
            description = "El usuario no existe o no es un gerente.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ManagedAreasResponse replace(
        @Parameter(description = "Identificador del gerente.", required = true) @PathParam("id") UUID managerId,
        @Valid ManagedAreasRequest request
    ) {
        SessionUser actor = currentSession.require();
        return ManagedAreasResponse.from(
            replaceManagedAreas.replace(
                managerId, actor, request.areaIds(), context.requestId(), context.clientIp()));
    }
}
