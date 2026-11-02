package me.esturafd.demo.archivos.auth.domain;

import me.esturafd.demo.archivos.auth.domain.RateLimitState;

import java.time.Duration;
import java.time.Instant;

/**
 * Regla pura que decide si un intento de inicio de sesión está bloqueado.
 *
 * <p>La base de datos solo guarda el estado (contador y ventana); la política
 * —cuántos intentos y durante cuánto— es de la aplicación y vive aquí.
 */
public final class LoginAttemptPolicy {

    private final int maxAttempts;
    private final Duration window;

    public LoginAttemptPolicy(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /**
     * Un intento está bloqueado si hay un bloqueo explícito vigente, o si dentro
     * de la ventana actual se alcanzó el máximo de intentos fallidos.
     */
    public boolean isBlocked(RateLimitState state, Instant now) {
        if (state == null) {
            return false;
        }
        if (state.blockedUntil() != null && state.blockedUntil().isAfter(now)) {
            return true;
        }
        if (state.windowStartedAt() == null) {
            return false;
        }
        boolean withinWindow = state.windowStartedAt().plus(window).isAfter(now);
        return withinWindow && state.failedCount() >= maxAttempts;
    }
}
