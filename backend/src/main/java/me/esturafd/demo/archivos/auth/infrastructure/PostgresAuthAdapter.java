package me.esturafd.demo.archivos.auth.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.auth.application.AuthDataPort;
import me.esturafd.demo.archivos.auth.application.model.LoginUser;
import me.esturafd.demo.archivos.auth.application.model.SessionSummary;
import me.esturafd.demo.archivos.auth.domain.RateLimitState;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;
import me.esturafd.demo.archivos.shared.jdbc.SqlExecutor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `auth` sobre PostgreSQL.
 *
 * <p>Es el único que conoce el SQL de identidad, sesiones propias y límite de
 * intentos. El ciclo de vida de la conexión, la transacción y el contexto los
 * aporta {@link JdbcDatabase}.
 */
@ApplicationScoped
public class PostgresAuthAdapter implements AuthDataPort {

    @Inject
    JdbcDatabase database;

    @Override
    public Optional<LoginUser> findUserForLogin(String email) {
        return database.inConnection(sql -> sql.queryOne("""
            SELECT id, email, password_hash, is_active, role, area_id
              FROM app.login_lookup(?)
            """, PostgresAuthAdapter::mapLoginUser, email));
    }

    @Override
    public UUID createLoginSession(
        UUID userId,
        String tokenHash,
        String ipAddress,
        String userAgent,
        Duration ttl,
        String email,
        String requestId
    ) {
        return database.inTransaction(userId, requestId, ipAddress, sql -> {
            UUID sessionId = sql.queryOne(
                "SELECT app.create_session(?::uuid, ?, ?::inet, ?, ?::interval)",
                rows -> rows.getObject(1, UUID.class),
                userId, tokenHash, ipAddress, userAgent, ttl.toString()
            ).orElseThrow(() -> ApiException.internalError(
                new IllegalStateException("create_session no devolvió identificador")));

            appendEvent(sql, "login_succeeded", userId, email, null, "{}");
            return sessionId;
        });
    }

    @Override
    public void recordFailedLogin(
        String attemptedEmail,
        UUID userId,
        String reason,
        String requestId,
        String ipAddress
    ) {
        database.inTransaction(null, requestId, ipAddress, sql -> {
            appendEvent(sql, "login_failed", userId, attemptedEmail, null,
                "{\"motivo\":\"" + reason + "\"}");
            return null;
        });
    }

    @Override
    public boolean closeSession(
        UUID userId,
        String tokenHash,
        String email,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(userId, requestId, ipAddress, sql -> {
            boolean revoked = sql.queryOne(
                "SELECT app.revoke_session(?)", rows -> rows.getBoolean(1), tokenHash
            ).orElse(false);
            if (revoked) {
                appendEvent(sql, "logout", userId, email, null, "{}");
            }
            return revoked;
        });
    }

    @Override
    public List<SessionSummary> listUserSessions(UUID userId, String requestId, String ipAddress) {
        return database.inTransaction(userId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT id, created_at, expires_at, last_seen_at,
                   ip_address::text AS ip_address, user_agent
              FROM app.list_sessions(?::uuid)
            """, PostgresAuthAdapter::mapSessionSummary, userId));
    }

    @Override
    public boolean revokeUserSession(
        UUID actorUserId,
        UUID sessionId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorUserId, requestId, ipAddress, sql -> sql.queryOne(
            "SELECT app.revoke_session_by_id(?::uuid)", rows -> rows.getBoolean(1), sessionId
        ).orElse(false));
    }

    @Override
    public Optional<RateLimitState> findRateLimit(String keyHash, String requestId, String ipAddress) {
        return database.inConnection(sql -> sql.queryOne("""
            SELECT failed_count, window_started_at, blocked_until
              FROM app.login_rate_limits
             WHERE key_hash = ?
            """, PostgresAuthAdapter::mapRateLimit, keyHash));
    }

    @Override
    public void recordFailedAttempt(String keyHash, Duration window, String requestId, String ipAddress) {
        // La ventana se reinicia sola: si la anterior ya expiró, el contador
        // vuelve a 1. La decisión de bloquear la toma la aplicación a partir del
        // estado, no esta sentencia.
        database.inConnection(sql -> {
            sql.run("""
                INSERT INTO app.login_rate_limits (key_hash, failed_count)
                VALUES (?, 1)
                ON CONFLICT (key_hash) DO UPDATE
                   SET failed_count = CASE
                         WHEN app.login_rate_limits.window_started_at < now() - ?::interval
                           THEN 1
                         ELSE app.login_rate_limits.failed_count + 1
                       END,
                       window_started_at = CASE
                         WHEN app.login_rate_limits.window_started_at < now() - ?::interval
                           THEN now()
                         ELSE app.login_rate_limits.window_started_at
                       END
                """, keyHash, window.toString(), window.toString());
            return null;
        });
    }

    @Override
    public void clearRateLimit(String keyHash, String requestId, String ipAddress) {
        database.inConnection(sql -> {
            sql.run("DELETE FROM app.login_rate_limits WHERE key_hash = ?", keyHash);
            return null;
        });
    }

    /**
     * Escribe un evento del historial. El correo del actor se envía con cast
     * explícito porque puede ser {@code null} (login fallido de un correo que no
     * existe).
     */
    private static void appendEvent(
        SqlExecutor sql,
        String action,
        UUID actorUserId,
        String actorEmail,
        UUID fileId,
        String metadata
    ) {
        sql.run("""
            SELECT audit.append_event(
                     ?::audit.audit_action, ?::uuid, ?::text, ?::uuid, NULL::text, ?::jsonb)
            """, action, actorUserId, actorEmail, fileId, metadata);
    }

    private static LoginUser mapLoginUser(ResultSet rows) throws SQLException {
        return new LoginUser(
            rows.getObject("id", UUID.class),
            rows.getString("email"),
            rows.getString("password_hash"),
            rows.getBoolean("is_active"),
            rows.getString("role"),
            rows.getObject("area_id", UUID.class)
        );
    }

    private static SessionSummary mapSessionSummary(ResultSet rows) throws SQLException {
        return new SessionSummary(
            rows.getObject("id", UUID.class),
            instant(rows, "created_at"),
            instant(rows, "expires_at"),
            instant(rows, "last_seen_at"),
            rows.getString("ip_address"),
            rows.getString("user_agent")
        );
    }

    private static RateLimitState mapRateLimit(ResultSet rows) throws SQLException {
        return new RateLimitState(
            rows.getInt("failed_count"),
            instant(rows, "window_started_at"),
            instant(rows, "blocked_until")
        );
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
