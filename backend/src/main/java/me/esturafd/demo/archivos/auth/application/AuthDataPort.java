package me.esturafd.demo.archivos.auth.application;

import me.esturafd.demo.archivos.auth.application.model.LoginUser;
import me.esturafd.demo.archivos.auth.application.model.RateLimitCheck;
import me.esturafd.demo.archivos.auth.application.model.SessionSummary;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de datos del módulo de autenticación.
 *
 * <p>Declara solo lo que `auth` necesita: identidad para el login, ciclo de vida
 * de la sesión propia y el límite de intentos. La resolución de la sesión en
 * curso no está aquí: es transversal y vive en `shared/security`.
 */
public interface AuthDataPort {

    /** Resuelve un usuario por correo para el inicio de sesión. */
    Optional<LoginUser> findUserForLogin(String email);

    /** Crea la sesión y registra {@code login_succeeded} en una única transacción. */
    UUID createLoginSession(
        UUID userId,
        String tokenHash,
        String ipAddress,
        String userAgent,
        Duration ttl,
        String email,
        String requestId
    );

    /** Registra un intento de inicio de sesión fallido en el historial. */
    void recordFailedLogin(
        String attemptedEmail,
        UUID userId,
        String reason,
        String requestId,
        String ipAddress
    );

    /** Revoca la sesión propia y registra {@code logout} en una única transacción. */
    boolean closeSession(
        UUID userId,
        String tokenHash,
        String email,
        String requestId,
        String ipAddress
    );

    /** Sesiones activas del usuario indicado. */
    List<SessionSummary> listUserSessions(UUID userId, String requestId, String ipAddress);

    /** Revoca una sesión concreta; solo su dueño o un administrador. */
    boolean revokeUserSession(UUID actorUserId, UUID sessionId, String requestId, String ipAddress);

    /**
     * Estado actual del limitador, junto con la hora de la base con la que
     * evaluarlo. Devolver la hora evita comparar el reloj de la aplicación con el
     * de la base: toda la decisión usa un único reloj.
     */
    RateLimitCheck findRateLimit(String keyHash, String requestId, String ipAddress);

    /** Suma un intento fallido, reiniciando la ventana si expiró. */
    void recordFailedAttempt(String keyHash, Duration window, String requestId, String ipAddress);

    /** Limpia el limitador, tras un inicio de sesión correcto. */
    void clearRateLimit(String keyHash, String requestId, String ipAddress);
}
