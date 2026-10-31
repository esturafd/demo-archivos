package me.esturafd.demo.archivos.shared.security.api;

import jakarta.enterprise.context.RequestScoped;
import me.esturafd.demo.archivos.shared.data.application.model.SessionUser;
import me.esturafd.demo.archivos.shared.error.application.ApiException;

import java.util.Optional;
import java.util.UUID;

/**
 * Sesión resuelta para la petición en curso.
 *
 * <p>La llena {@link SessionFilter} a partir de la cookie. Vive en la capa de
 * API porque es donde se conoce HTTP.
 */
@RequestScoped
public class CurrentSession {

    private SessionUser user;
    private UUID sessionId;
    private String tokenHash;

    public Optional<SessionUser> user() {
        return Optional.ofNullable(user);
    }

    public Optional<UUID> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    /** Hash del token de la sesión actual; lo usa el cierre de sesión. */
    public String tokenHash() {
        return tokenHash;
    }

    /** Usuario de la sesión actual, o lanza {@code 401} si no hay sesión. */
    public SessionUser require() {
        if (user == null) {
            throw ApiException.unauthenticated();
        }
        return user;
    }

    void resolve(SessionUser user, UUID sessionId, String tokenHash) {
        this.user = user;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
    }
}
