package me.esturafd.demo.archivos.auth.api;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.esturafd.demo.archivos.auth.api.dto.CurrentSessionResponse;
import me.esturafd.demo.archivos.auth.api.dto.LoginRequest;
import me.esturafd.demo.archivos.auth.api.dto.SessionView;
import me.esturafd.demo.archivos.auth.api.dto.SessionsResponse;
import me.esturafd.demo.archivos.auth.api.dto.UserView;
import me.esturafd.demo.archivos.auth.application.LoginResult;
import me.esturafd.demo.archivos.auth.application.LoginUseCase;
import me.esturafd.demo.archivos.auth.application.LogoutUseCase;
import me.esturafd.demo.archivos.auth.application.QuerySessions;
import me.esturafd.demo.archivos.auth.application.RevokeSessionUseCase;
import me.esturafd.demo.archivos.shared.data.application.model.SessionUser;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.api.SessionCookies;

/**
 * Recurso de autenticación y sesiones.
 *
 * <p>El token de sesión viaja en una cookie {@code HttpOnly}; nunca aparece en el
 * cuerpo de la respuesta. El cierre de sesión revoca la sesión en el servidor,
 * no solo en el navegador.
 */
@Path("/api/auth/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthSessionResource {

    @Inject
    LoginUseCase loginUseCase;

    @Inject
    LogoutUseCase logoutUseCase;

    @Inject
    QuerySessions querySessions;

    @Inject
    RevokeSessionUseCase revokeSessionUseCase;

    @Inject
    CurrentSession currentSession;

    @Inject
    SessionCookies cookies;

    @Inject
    RequestContext context;

    @Context
    HttpHeaders headers;

    /** Inicia sesión. Devuelve la cookie y el usuario; el token no va en el cuerpo. */
    @POST
    public Response create(@Valid LoginRequest request) {
        String ipAddress = context.clientIp();
        String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);

        LoginResult result = loginUseCase.login(
            request.email(),
            request.password(),
            ipAddress,
            userAgent,
            context.requestId()
        );

        return switch (result.outcome()) {
            case SUCCESS -> Response.status(Response.Status.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.token()))
                .header(HttpHeaders.LOCATION, "/api/auth/sessions/current")
                .entity(new CurrentSessionResponse(
                    UserView.from(result.user()),
                    getCurrentSession(result.user().id(), result.sessionId())
                ))
                .build();
            case INVALID_CREDENTIALS -> throw ApiException.unauthenticated();
            case TOO_MANY_ATTEMPTS -> throw ApiException.tooManyRequests();
        };
    }

    /** Usuario y sesión actuales. */
    @GET
    @Path("/current")
    @RequiresSession
    public CurrentSessionResponse current() {
        SessionUser user = currentSession.require();
        return new CurrentSessionResponse(
            UserView.from(user),
            getCurrentSession(user.id(), currentSession.sessionId().orElse(null))
        );
    }

    /** Sesiones activas propias. */
    @GET
    @RequiresSession
    public SessionsResponse list() {
        SessionUser user = currentSession.require();
        UUID currentId = currentSession.sessionId().orElse(null);
        List<SessionView> sessions = querySessions
            .list(user.id(), context.requestId(), context.clientIp())
            .stream()
            .map(session -> SessionView.from(session, currentId))
            .toList();
        return new SessionsResponse(sessions);
    }

    /** Cierra la sesión actual. */
    @DELETE
    @Path("/current")
    @RequiresSession
    public Response close() {
        SessionUser user = currentSession.require();
        logoutUseCase.logout(
            user,
            currentSession.tokenHash(),
            context.requestId(),
            context.clientIp()
        );
        return Response.noContent()
            .header(HttpHeaders.SET_COOKIE, cookies.clear())
            .build();
    }

    /** Revoca una sesión concreta. Solo su dueño o un administrador. */
    @DELETE
    @Path("/{id}")
    @RequiresSession
    public Response revoke(@PathParam("id") UUID id) {
        SessionUser user = currentSession.require();
        boolean revoked = revokeSessionUseCase.revoke(
            user.id(),
            id,
            context.requestId(),
            context.clientIp()
        );
        if (!revoked) {
            throw ApiException.notFound();
        }
        return Response.noContent().build();
    }

    private SessionView getCurrentSession(UUID userId, UUID sessionId) {
        return querySessions
            .current(userId, sessionId, context.requestId(), context.clientIp())
            .map(session -> SessionView.from(session, sessionId))
            .orElse(null);
    }
}
