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
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.application.SessionResolutionPort;
import me.esturafd.demo.archivos.shared.security.application.SessionToken;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve la sesión de cada petición a partir de la cookie.
 *
 * <p>Si hay cookie, la valida contra la base de datos y deja el usuario en
 * {@link CurrentSession}. Después aplica las guardas de ruta: {@link
 * RequiresSession} responde {@code 401} sin sesión, y {@link RequiresRole}
 * responde {@code 403} si el rol no alcanza el nivel exigido.
 *
 * <p>No decide el alcance fino: eso vive en la base de datos.
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

        if (findAnnotation(RequiresSession.class) != null && currentSession.user().isEmpty()) {
            throw ApiException.unauthenticated();
        }

        RequiresRole requiresRole = findAnnotation(RequiresRole.class);
        if (requiresRole != null) {
            SessionUser user = currentSession.user().orElseThrow(ApiException::unauthenticated);
            boolean allowed = AppRole.from(user.role())
                .map(role -> role.atLeast(requiresRole.value()))
                .orElse(false);
            if (!allowed) {
                throw ApiException.forbidden();
            }
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

    /** Busca la anotación primero en el método y después en su clase. */
    private <T extends Annotation> T findAnnotation(Class<T> type) {
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            T onMethod = method.getAnnotation(type);
            if (onMethod != null) {
                return onMethod;
            }
            T onClass = method.getDeclaringClass().getAnnotation(type);
            if (onClass != null) {
                return onClass;
            }
        }
        Class<?> resource = resourceInfo.getResourceClass();
        return resource == null ? null : resource.getAnnotation(type);
    }
}
