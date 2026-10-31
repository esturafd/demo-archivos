package me.esturafd.demo.archivos.auth.domain;

import java.time.Instant;

/**
 * Estado del límite de intentos de inicio de sesión para una clave
 * (correo + IP).
 *
 * @param failedCount     intentos fallidos acumulados en la ventana
 * @param windowStartedAt cuándo empezó la ventana
 * @param blockedUntil    hasta cuándo está bloqueado, o {@code null}
 */
public record RateLimitState(
    int failedCount,
    Instant windowStartedAt,
    Instant blockedUntil
) {
}
