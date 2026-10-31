package me.esturafd.demo.archivos.shared.security.api;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.security.application.SessionResolutionPort;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.application.SessionToken;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve la sesión de cada petición a partir de la cookie.
 *
 * <p>Si hay cookie, la valida contra la base de datos y deja el usuario en
 * {@link CurrentSession}. Si el recurso exige sesión y no la hay, responde
 * {@code 401}. No decide permisos: eso vive en la base de datos.
 *
 * <p>Va marcado con {@code @Blocking} porque consulta la base de datos; así el
 * filtro y el recurso corren en un hilo de trabajo y no bloquean el bucle de
 * eventos.
 */
@Blocking
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SessionFilter implements ContainerRequestFilter {

    @Inject
    SessionResolutionPort sessions;

    @Inject
    RequestContext context;

    @Inject
    CurrentSession currentSession;

    @Inject
    SessionCookies cookies;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext request) {
        resolveSession(request);
        if (requiresSession() && currentSession.user().isEmpty()) {
            throw ApiException.unauthenticated();
        }
        if (requiresAdmin() && !currentSession.user().orElseThrow(ApiException::unauthenticated).isAdmin()) {
            throw ApiException.forbidden();
        }
    }

    private void resolveSession(ContainerRequestContext request) {
        Cookie cookie = request.getCookies().get(cookies.name());
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return;
        }

        String tokenHash = SessionToken.hash(cookie.getValue());
        Optional<UUID> userId = sessions.validateToken(tokenHash);
        if (userId.isEmpty()) {
            return;
        }

        UUID sessionId = sessions.findSessionId(tokenHash).orElse(null);
        sessions.findUser(userId.get(), context.requestId(), context.clientIp())
            .ifPresent(user -> currentSession.resolve(user, sessionId, tokenHash));
    }

    private boolean requiresSession() {
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            if (method.isAnnotationPresent(RequiresSession.class)
                || method.getDeclaringClass().isAnnotationPresent(RequiresSession.class)) {
                return true;
            }
        }
        Class<?> resource = resourceInfo.getResourceClass();
        return resource != null && resource.isAnnotationPresent(RequiresSession.class);
    }

    private boolean requiresAdmin() {
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            if (method.isAnnotationPresent(RequiresAdmin.class)
                || method.getDeclaringClass().isAnnotationPresent(RequiresAdmin.class)) {
                return true;
            }
        }
        Class<?> resource = resourceInfo.getResourceClass();
        return resource != null && resource.isAnnotationPresent(RequiresAdmin.class);
    }
}
