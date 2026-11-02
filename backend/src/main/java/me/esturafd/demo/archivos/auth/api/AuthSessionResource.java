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
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.api.SessionCookies;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirementsSet;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
@Tag(name = "Autenticación")
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
    @Operation(
        summary = "Iniciar sesión",
        description = """
            Valida el correo y la contraseña y emite la cookie de sesión. Es la única
            operación pública: no exige sesión ni token CSRF previo.
            """
    )
    @SecurityRequirements({})
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Sesión iniciada. La cookie `session` viaja en `Set-Cookie`.",
            headers = @Header(
                name = "Set-Cookie",
                description = "Cookie de sesión `HttpOnly` y su cookie CSRF `csrf-token`."),
            content = @Content(schema = @Schema(implementation = CurrentSessionResponse.class))),
        @APIResponse(
            responseCode = "400",
            description = "El cuerpo no supera la validación.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "401",
            description = "Credenciales inválidas.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "429",
            description = "Demasiados intentos. Vuelve a intentarlo más tarde.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(
        summary = "Sesión actual",
        description = "Usuario autenticado y datos de la sesión con la que se navega."
    )
    @APIResponse(
        responseCode = "200",
        description = "Usuario y sesión actuales.",
        content = @Content(schema = @Schema(implementation = CurrentSessionResponse.class)))
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
    @Operation(
        summary = "Sesiones activas",
        description = "Sesiones vigentes del propio usuario, con la actual marcada."
    )
    @APIResponse(
        responseCode = "200",
        description = "Sesiones del usuario.",
        content = @Content(schema = @Schema(implementation = SessionsResponse.class)))
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
    @Operation(
        summary = "Cerrar sesión",
        description = "Revoca la sesión actual en el servidor y limpia la cookie."
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponse(
        responseCode = "204",
        description = "Sesión cerrada. La cookie se limpia con `Set-Cookie`.")
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
    @Operation(
        summary = "Revocar una sesión",
        description = "Revoca una sesión concreta. Solo su dueño o un administrador."
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Sesión revocada."),
        @APIResponse(
            responseCode = "404",
            description = "La sesión no existe o no es del usuario.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Response revoke(
        @Parameter(description = "Identificador de la sesión.", required = true) @PathParam("id") UUID id
    ) {
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
