package me.esturafd.demo.archivos.users.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;
import me.esturafd.demo.archivos.shared.jdbc.SqlExecutor;
import me.esturafd.demo.archivos.users.application.UsersDataPort;
import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `users` sobre PostgreSQL.
 *
 * <p>Es el único que conoce el SQL de los usuarios. Nunca lee ni devuelve
 * {@code password_hash}. El alta y la edición comprueban las filas afectadas con
 * {@code RETURNING} y luego vuelven a leer el usuario en la **misma
 * transacción**: no se puede leer desde el propio {@code RETURNING} del
 * {@code INSERT}/{@code UPDATE} porque los CTE que modifican datos no ven sus
 * cambios.
 */
@ApplicationScoped
public class PostgresUsersAdapter implements UsersDataPort {

    private static final String COLUMNS =
        "u.id, u.email, u.name, u.role, u.area_id, a.name AS area_name, u.is_active, u.created_at";

    @Inject
    JdbcDatabase database;

    @Override
    public List<UserDetail> listVisibleUsers(
        String search,
        UUID areaId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT %s
              FROM app.users u
              LEFT JOIN app.areas a ON a.id = u.area_id
             WHERE (?::text IS NULL
                    OR u.email ILIKE '%%' || ?::text || '%%'
                    OR u.name  ILIKE '%%' || ?::text || '%%')
               AND (?::uuid IS NULL OR u.area_id = ?::uuid)
             ORDER BY lower(u.email)
            """.formatted(COLUMNS), PostgresUsersAdapter::mapUser,
            search, search, search, areaId, areaId));
    }

    @Override
    public Optional<UserDetail> findVisibleUser(
        UUID userId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> readUser(sql, userId));
    }

    @Override
    public UserDetail createUser(
        UUID actorId,
        String email,
        String passwordHash,
        String name,
        String role,
        UUID areaId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> {
            UUID userId = sql.queryOne("""
                INSERT INTO app.users (email, password_hash, name, role, area_id)
                VALUES (lower(?::text), ?::text, ?::text, ?::app.app_role, ?::uuid)
                RETURNING id
                """, rows -> rows.getObject(1, UUID.class), email, passwordHash, name, role, areaId)
                .orElseThrow(() -> ApiException.internalError(
                    new IllegalStateException("La inserción del usuario no devolvió fila")));

            return readUser(sql, userId).orElseThrow(() -> ApiException.internalError(
                new IllegalStateException("El usuario recién creado no se pudo leer")));
        });
    }

    @Override
    public Optional<UserDetail> updateUser(
        UUID actorId,
        UUID userId,
        String name,
        String role,
        UUID areaId,
        boolean active,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> {
            Optional<UUID> updated = sql.queryOne("""
                UPDATE app.users
                   SET name = ?::text,
                       role = ?::app.app_role,
                       area_id = ?::uuid,
                       is_active = ?,
                       deactivated_at = CASE WHEN ? THEN NULL ELSE now() END
                 WHERE id = ?::uuid
                RETURNING id
                """, rows -> rows.getObject(1, UUID.class),
                name, role, areaId, active, active, userId);

            if (updated.isEmpty()) {
                return Optional.<UserDetail>empty();
            }

            // Desactivar revoca las sesiones en la misma transacción.
            if (!active) {
                sql.run("SELECT app.revoke_user_sessions(?::uuid)", userId);
            }

            return readUser(sql, userId);
        });
    }

    private static Optional<UserDetail> readUser(SqlExecutor sql, UUID userId) {
        return sql.queryOne("""
            SELECT %s
              FROM app.users u
              LEFT JOIN app.areas a ON a.id = u.area_id
             WHERE u.id = ?::uuid
            """.formatted(COLUMNS), PostgresUsersAdapter::mapUser, userId);
    }

    private static UserDetail mapUser(ResultSet rows) throws SQLException {
        return new UserDetail(
            rows.getObject("id", UUID.class),
            rows.getString("email"),
            rows.getString("name"),
            rows.getString("role"),
            rows.getObject("area_id", UUID.class),
            rows.getString("area_name"),
            rows.getBoolean("is_active"),
            instant(rows, "created_at")
        );
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
