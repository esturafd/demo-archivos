package me.esturafd.demo.archivos.audit.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.audit.application.AuditDataPort;
import me.esturafd.demo.archivos.audit.application.model.AuditEvent;
import me.esturafd.demo.archivos.audit.application.model.AuditSearch;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `audit` sobre PostgreSQL.
 *
 * <p>Es de solo lectura: nunca escribe en el historial. El alcance lo aplica RLS,
 * que usa el área que el actor tenía al ejecutar la acción.
 */
@ApplicationScoped
public class PostgresAuditAdapter implements AuditDataPort {

    private static final String COLUMNS = """
        l.id, l.occurred_at, l.action, l.actor_user_id, l.actor_email,
        l.actor_role, l.actor_area_name, l.file_id, l.file_name,
        l.ip_address::text AS ip_address, l.request_id, l.metadata
        """;

    @Inject
    JdbcDatabase database;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public List<AuditEvent> listVisibleEvents(
        AuditSearch search,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT %s
              FROM audit.action_logs l
             WHERE (?::audit.audit_action IS NULL OR l.action = ?::audit.audit_action)
               AND (?::uuid IS NULL OR l.actor_user_id = ?::uuid)
               AND (?::timestamptz IS NULL
                    OR (l.occurred_at, l.id) < (?::timestamptz, ?::bigint))
             ORDER BY l.occurred_at DESC, l.id DESC
             LIMIT ?
            """.formatted(COLUMNS), this::mapEvent,
            search.action(), search.action(),
            search.actorId(), search.actorId(),
            timestamp(search.cursorOccurredAt()), timestamp(search.cursorOccurredAt()), search.cursorId(),
            search.limit() + 1));
    }

    @Override
    public Optional<AuditEvent> findVisibleEvent(
        long eventId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryOne("""
            SELECT %s
              FROM audit.action_logs l
             WHERE l.id = ?::bigint
            """.formatted(COLUMNS), this::mapEvent, eventId));
    }

    private AuditEvent mapEvent(ResultSet rows) throws SQLException {
        return new AuditEvent(
            rows.getLong("id"),
            instant(rows, "occurred_at"),
            rows.getString("action"),
            rows.getObject("actor_user_id", UUID.class),
            rows.getString("actor_email"),
            rows.getString("actor_role"),
            rows.getString("actor_area_name"),
            rows.getObject("file_id", UUID.class),
            rows.getString("file_name"),
            rows.getString("ip_address"),
            rows.getString("request_id"),
            metadata(rows.getString("metadata"))
        );
    }

    /** El historial guarda los metadatos como jsonb; se devuelven como objeto. */
    private Map<String, Object> metadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception invalid) {
            return Map.of();
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
