package me.esturafd.demo.archivos.shared.security.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;
import me.esturafd.demo.archivos.shared.security.application.SessionResolutionPort;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve la sesión en curso contra PostgreSQL.
 *
 * <p>Solo conoce las consultas de resolución; el resto del ciclo de vida de la
 * sesión lo cubre `auth`.
 */
@ApplicationScoped
public class PostgresSessionResolutionAdapter implements SessionResolutionPort {

    @Inject
    JdbcDatabase database;

    @Override
    public Optional<UUID> validateToken(String tokenHash) {
        return database.inConnection(sql -> sql.queryOne(
            "SELECT app.validate_session(?)", rows -> rows.getObject(1, UUID.class), tokenHash));
    }

    @Override
    public Optional<UUID> findSessionId(String tokenHash) {
        return database.inConnection(sql -> sql.queryOne(
            "SELECT app.session_id_for_token(?)", rows -> rows.getObject(1, UUID.class), tokenHash));
    }

    @Override
    public Optional<SessionUser> findUser(UUID userId, String requestId, String ipAddress) {
        return database.inTransaction(userId, requestId, ipAddress, sql -> sql.queryOne("""
            SELECT u.id, u.email, u.name, u.role, u.area_id, u.is_active,
                   a.name AS area_name, a.is_active AS area_active
              FROM app.users u
              LEFT JOIN app.areas a ON a.id = u.area_id
             WHERE u.id = ?
            """, PostgresSessionResolutionAdapter::mapSessionUser, userId));
    }

    private static SessionUser mapSessionUser(ResultSet rows) throws SQLException {
        return new SessionUser(
            rows.getObject("id", UUID.class),
            rows.getString("email"),
            rows.getString("name"),
            rows.getString("role"),
            rows.getObject("area_id", UUID.class),
            rows.getBoolean("is_active"),
            rows.getString("area_name"),
            rows.getObject("area_active", Boolean.class)
        );
    }
}
