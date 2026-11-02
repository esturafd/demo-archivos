package me.esturafd.demo.archivos.auth.application;

import me.esturafd.demo.archivos.auth.domain.LoginOutcome;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Resultado de un intento de inicio de sesión.
 *
 * <p>Solo el caso correcto lleva token y datos de sesión.
 */
public record LoginResult(
    LoginOutcome outcome,
    String token,
    SessionUser user,
    UUID sessionId
) {

    static LoginResult success(String token, SessionUser user, UUID sessionId) {
        return new LoginResult(LoginOutcome.SUCCESS, token, user, sessionId);
    }

    static LoginResult invalidCredentials() {
        return new LoginResult(LoginOutcome.INVALID_CREDENTIALS, null, null, null);
    }

    static LoginResult tooManyAttempts() {
        return new LoginResult(LoginOutcome.TOO_MANY_ATTEMPTS, null, null, null);
    }
}
