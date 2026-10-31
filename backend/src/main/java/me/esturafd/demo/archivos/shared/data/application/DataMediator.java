package me.esturafd.demo.archivos.shared.data.application;

import me.esturafd.demo.archivos.shared.data.application.model.LoginUser;
import me.esturafd.demo.archivos.shared.data.application.model.RateLimitState;
import me.esturafd.demo.archivos.shared.data.application.model.SessionSummary;
import me.esturafd.demo.archivos.shared.data.application.model.SessionUser;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Único punto de acceso a la información persistente.
 *
 * <p>Es el mediador de datos: sabe cómo se guardan y se protegen los datos, y
 * esconde el proveedor concreto. El resto de la aplicación depende solo de esta
 * interfaz, de modo que cambiar de proveedor —o mover a otro sitio
 * responsabilidades que hoy están en la base de datos— no obligue a tocar los
 * casos de uso.
 *
 * <p>Cada operación que trabaja con datos abre una transacción, fija el
 * contexto de la petición ({@code app.set_context}) y confirma o revierte. La
 * interfaz se expresa en operaciones del dominio, no como un ejecutor genérico
 * de consultas, y se organiza por secciones.
 */
public interface DataMediator {

    /**
     * Comprueba que el proveedor responde.
     *
     * <p>No lanza excepciones: un fallo de conexión es precisamente la respuesta
     * que se espera evaluar.
     */
    boolean isAvailable();

    // ------------------------------------------------------------------
    // Identidad y sesiones
    // ------------------------------------------------------------------

    /**
     * Resuelve un usuario por correo para el inicio de sesión.
     *
     * <p>Ocurre antes de que exista contexto de usuario, así que se apoya en
     * {@code app.login_lookup}. No distingue mayúsculas de minúsculas.
     */
    Optional<LoginUser> findUserForLogin(String email);

    /**
     * Crea la sesión y registra el evento {@code login_succeeded} en una única
     * transacción: o se crean ambos, o ninguno.
     *
     * @return el identificador de la sesión creada
     */
    UUID createLoginSession(
        UUID userId,
        String tokenHash,
        String ipAddress,
        String userAgent,
        Duration ttl,
        String email,
        String requestId
    );

    /**
     * Registra un intento de inicio de sesión fallido en el historial.
     *
     * @param userId usuario resuelto, si el correo existía; {@code null} si no
     * @param reason motivo interno; nunca se expone al cliente
     */
    void recordFailedLogin(
        String attemptedEmail,
        UUID userId,
        String reason,
        String requestId,
        String ipAddress
    );

    /**
     * Revoca la sesión y registra el evento {@code logout} en una única
     * transacción.
     *
     * @return {@code true} si la sesión existía y quedó revocada
     */
    boolean closeSession(
        UUID userId,
        String tokenHash,
        String email,
        String requestId,
        String ipAddress
    );

    /**
     * Comprueba un token de sesión y devuelve el usuario al que pertenece.
     *
     * <p>No requiere contexto: el token se resuelve antes de conocer al usuario.
     */
    Optional<UUID> validateSessionToken(String tokenHash);

    /**
     * Identificador de la sesión activa correspondiente a un token.
     *
     * <p>Permite distinguir la sesión actual dentro del listado de dispositivos.
     */
    Optional<UUID> findSessionIdForToken(String tokenHash);

    /** Datos del usuario de la sesión actual, con el alcance vigente. */
    Optional<SessionUser> findSessionUser(UUID userId, String requestId, String ipAddress);

    /** Sesiones activas del usuario indicado. */
    List<SessionSummary> listUserSessions(UUID userId, String requestId, String ipAddress);

    /**
     * Revoca una sesión concreta por su identificador.
     *
     * @param actorUserId quién realiza la operación; solo su dueño o un
     *                    administrador podrán revocarla
     * @return {@code true} si la sesión existía, estaba activa y se pudo revocar
     */
    boolean revokeUserSession(UUID actorUserId, UUID sessionId, String requestId, String ipAddress);

    // ------------------------------------------------------------------
    // Límite de intentos de inicio de sesión
    // ------------------------------------------------------------------

    /** Estado actual del limitador para la clave indicada. */
    Optional<RateLimitState> findRateLimit(String keyHash, String requestId, String ipAddress);

    /** Suma un intento fallido para la clave indicada, reiniciando la ventana si expiró. */
    void recordFailedAttempt(String keyHash, Duration window, String requestId, String ipAddress);

    /** Limpia el estado del limitador, tras un inicio de sesión correcto. */
    void clearRateLimit(String keyHash, String requestId, String ipAddress);
}
