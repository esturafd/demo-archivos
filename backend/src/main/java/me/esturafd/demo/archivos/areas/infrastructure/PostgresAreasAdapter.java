package me.esturafd.demo.archivos.areas.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.areas.application.AreasDataPort;
import me.esturafd.demo.archivos.areas.application.model.Area;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `areas` sobre PostgreSQL.
 *
 * <p>Es el único que conoce el SQL de las áreas. En la edición se usa
 * {@code coalesce} para conservar los campos que no se envían, y se comprueba
 * {@code RETURNING} porque RLS deniega el UPDATE en silencio: cero filas
 * significa que el área no existe o no es visible.
 */
@ApplicationScoped
public class PostgresAreasAdapter implements AreasDataPort {

    private static final String COLUMNS =
        "id, name, description, is_active, created_at, updated_at";

    @Inject
    JdbcDatabase database;

    @Override
    public List<Area> listVisibleAreas(UUID actorId, String requestId, String ipAddress) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT %s FROM app.areas ORDER BY lower(name)
            """.formatted(COLUMNS), PostgresAreasAdapter::mapArea));
    }

    @Override
    public Optional<Area> findVisibleArea(
        UUID areaId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryOne("""
            SELECT %s FROM app.areas WHERE id = ?::uuid
            """.formatted(COLUMNS), PostgresAreasAdapter::mapArea, areaId));
    }

    @Override
    public Area createArea(
        UUID actorId,
        String name,
        String description,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryOne("""
            INSERT INTO app.areas (name, description)
            VALUES (?, ?::text)
            RETURNING %s
            """.formatted(COLUMNS), PostgresAreasAdapter::mapArea, name, description)
            .orElseThrow(() -> ApiException.internalError(
                new IllegalStateException("La inserción del área no devolvió fila"))));
    }

    @Override
    public Optional<Area> updateArea(
        UUID actorId,
        UUID areaId,
        String name,
        String description,
        Boolean active,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryOne("""
            UPDATE app.areas
               SET name = coalesce(?::text, name),
                   description = coalesce(?::text, description),
                   is_active = coalesce(?::boolean, is_active)
             WHERE id = ?::uuid
            RETURNING %s
            """.formatted(COLUMNS), PostgresAreasAdapter::mapArea, name, description, active, areaId));
    }

    private static Area mapArea(ResultSet rows) throws SQLException {
        return new Area(
            rows.getObject("id", UUID.class),
            rows.getString("name"),
            rows.getString("description"),
            rows.getBoolean("is_active"),
            instant(rows, "created_at"),
            instant(rows, "updated_at")
        );
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
