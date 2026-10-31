package me.esturafd.demo.archivos.audit.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import me.esturafd.demo.archivos.audit.api.dto.AuditEventView;
import me.esturafd.demo.archivos.audit.api.dto.AuditPageResponse;
import me.esturafd.demo.archivos.audit.application.FindAuditEventUseCase;
import me.esturafd.demo.archivos.audit.application.ListAuditEventsUseCase;
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * Recurso del historial. Solo lectura: no hay endpoints de modificación.
 *
 * <p>El alcance lo aplica RLS: el administrador ve todo, el gerente sus acciones
 * y las de sus áreas. Estándar y jefe de área reciben {@code 403} antes de llegar
 * a la base, por la guarda de rol.
 *
 * <p>Exige nivel manager o superior, no un rol exacto.
 */
@Path("/api/audit/events")
@Produces(MediaType.APPLICATION_JSON)
@RequiresSession
@RequiresRole(AppRole.MANAGER)
@Tag(name = "Historial")
@APIResponses({
    @APIResponse(
        responseCode = "401",
        description = "No hay una sesión válida.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "403",
        description = "La sesión no alcanza el nivel de gerente.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "500",
        description = "Error inesperado.",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class AuditResource {

    @Inject
    ListAuditEventsUseCase listEvents;

    @Inject
    FindAuditEventUseCase findEvent;

    @Inject
    CurrentSession currentSession;

    @Inject
    RequestContext context;

    /** Lista el historial con filtros por acción y actor y paginación por cursor. */
    @GET
    @Operation(
        summary = "Listar el historial",
        description = """
            Eventos del historial, con filtros por acción y actor y paginación por
            cursor. El alcance lo acota RLS: el gerente ve sus acciones y las de sus
            áreas; el administrador, todo.
            """
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Página del historial.",
            content = @Content(schema = @Schema(implementation = AuditPageResponse.class))),
        @APIResponse(
            responseCode = "400",
            description = "La acción indicada no es conocida.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuditPageResponse list(
        @Parameter(description = "Acción a filtrar, por ejemplo `login_succeeded` o `file_uploaded`.")
        @QueryParam("action") String action,
        @Parameter(description = "Identificador del usuario cuyas acciones se quieren ver.")
        @QueryParam("actorId") UUID actorId,
        @Parameter(description = "Cursor opaco de la página anterior (`nextCursor`).")
        @QueryParam("cursor") String cursor,
        @Parameter(description = "Tamaño de página. Por defecto 20, máximo 100.")
        @QueryParam("limit") Integer limit
    ) {
        SessionUser actor = currentSession.require();
        return AuditPageResponse.from(listEvents.list(
            actor, action, actorId, cursor, limit, context.requestId(), context.clientIp()));
    }

    /** Consulta un evento por su identificador. */
    @GET
    @Path("/{id}")
    @Operation(
        summary = "Consultar un evento",
        description = "Un evento del historial por su identificador."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Evento del historial.",
            content = @Content(schema = @Schema(implementation = AuditEventView.class))),
        @APIResponse(
            responseCode = "404",
            description = "El evento no existe o queda fuera del alcance.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuditEventView find(
        @Parameter(description = "Identificador del evento.", required = true) @PathParam("id") long id
    ) {
        SessionUser actor = currentSession.require();
        return AuditEventView.from(findEvent.find(id, actor, context.requestId(), context.clientIp()));
    }
}
