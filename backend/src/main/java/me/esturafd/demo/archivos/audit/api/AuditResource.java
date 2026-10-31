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
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Recurso del historial. Solo lectura: no hay endpoints de modificación.
 *
 * <p>El alcance lo aplica RLS: el administrador ve todo, el gerente sus acciones
 * y las de sus áreas, y estándar y jefe de área no ven nada. Exige sesión, pero
 * no un rol concreto: el alcance lo decide la base.
 */
@Path("/api/audit/events")
@Produces(MediaType.APPLICATION_JSON)
@RequiresSession
@RequiresRole(AppRole.MANAGER)
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
    public AuditPageResponse list(
        @QueryParam("action") String action,
        @QueryParam("actorId") UUID actorId,
        @QueryParam("cursor") String cursor,
        @QueryParam("limit") Integer limit
    ) {
        SessionUser actor = currentSession.require();
        return AuditPageResponse.from(listEvents.list(
            actor, action, actorId, cursor, limit, context.requestId(), context.clientIp()));
    }

    /** Consulta un evento por su identificador. */
    @GET
    @Path("/{id}")
    public AuditEventView find(@PathParam("id") long id) {
        SessionUser actor = currentSession.require();
        return AuditEventView.from(findEvent.find(id, actor, context.requestId(), context.clientIp()));
    }
}
