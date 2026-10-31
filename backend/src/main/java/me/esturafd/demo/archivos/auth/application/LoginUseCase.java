package me.esturafd.demo.archivos.auth.application;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.auth.domain.LoginAttemptPolicy;
import me.esturafd.demo.archivos.auth.application.AuthDataPort;
import me.esturafd.demo.archivos.auth.application.model.LoginUser;
import me.esturafd.demo.archivos.auth.domain.RateLimitState;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.SessionResolutionPort;
import me.esturafd.demo.archivos.shared.security.application.SessionToken;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso de inicio de sesión.
 *
 * <p>Ordena el ciclo del enunciado: comprobar el límite de intentos, resolver
 * el usuario, verificar la contraseña, crear la sesión y registrar el evento. La
 * respuesta es la misma para correo inexistente, contraseña incorrecta y cuenta
 * desactivada.
 */
@ApplicationScoped
public class LoginUseCase {

    /**
     * Hash de descarte. Cuando el correo no existe se verifica igualmente
     * contra este hash para que el tiempo de respuesta no revele si la cuenta
     * existe. No corresponde a ninguna contraseña real.
     */
    private static final String DISCARD_HASH =
        "$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG";

    @Inject
    AuthDataPort data;

    @Inject
    SessionResolutionPort sessions;

    @Inject
    LoginAttemptKey attemptKey;

    @ConfigProperty(name = "app.auth.rate-limit.max-attempts")
    int maxAttempts;

    @ConfigProperty(name = "app.auth.rate-limit.window")
    Duration window;

    @ConfigProperty(name = "app.auth.session.ttl")
    Duration sessionTtl;

    public LoginResult login(
        String email,
        String password,
        String ipAddress,
        String userAgent,
        String requestId
    ) {
        String keyHash = attemptKey.of(email, ipAddress);
        RateLimitState state = data.findRateLimit(keyHash, requestId, ipAddress).orElse(null);

        LoginAttemptPolicy policy = new LoginAttemptPolicy(maxAttempts, window);
        if (policy.isBlocked(state, Instant.now())) {
            data.recordFailedLogin(email, null, "bloqueado", requestId, ipAddress);
            return LoginResult.tooManyAttempts();
        }

        Optional<LoginUser> found = data.findUserForLogin(email);

        // Se verifica siempre una vez, contra el hash real o el de descarte, para
        // que los tres casos de fallo cuesten lo mismo.
        boolean passwordMatches = BcryptUtil.matches(
            password,
            found.map(LoginUser::passwordHash).orElse(DISCARD_HASH)
        );

        if (found.isPresent() && found.get().active() && passwordMatches) {
            return succeed(found.get(), ipAddress, userAgent, requestId, keyHash);
        }

        String reason = found.isEmpty() ? "credenciales"
            : (found.get().active() ? "credenciales" : "inactivo");
        UUID knownUserId = found.map(LoginUser::id).orElse(null);
        data.recordFailedAttempt(keyHash, window, requestId, ipAddress);
        data.recordFailedLogin(email, knownUserId, reason, requestId, ipAddress);
        return LoginResult.invalidCredentials();
    }

    private LoginResult succeed(
        LoginUser user,
        String ipAddress,
        String userAgent,
        String requestId,
        String keyHash
    ) {
        String token = SessionToken.generate();
        UUID sessionId = data.createLoginSession(
            user.id(),
            SessionToken.hash(token),
            ipAddress,
            userAgent,
            sessionTtl,
            user.email(),
            requestId
        );
        data.clearRateLimit(keyHash, requestId, ipAddress);

        SessionUser sessionUser = sessions.findUser(user.id(), requestId, ipAddress)
            .orElseThrow(() -> ApiException.internalError(
                new IllegalStateException("La sesión recién creada no resolvió a su usuario")));

        return LoginResult.success(token, sessionUser, sessionId);
    }
}
