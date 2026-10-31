package me.esturafd.demo.archivos.managedareas.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.managedareas.application.ManagedAreasDataPort;
import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;
import me.esturafd.demo.archivos.shared.jdbc.SqlExecutor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `managedareas` sobre PostgreSQL.
 *
 * <p>Trabaja sobre {@code app.manager_areas}. El reemplazo borra e inserta en una
 * sola transacción; si un área no existe o está inactiva, el error revierte el
 * borrado y no queda un estado intermedio.
 */
@ApplicationScoped
public class PostgresManagedAreasAdapter implements ManagedAreasDataPort {

    private static final String SELECT_MANAGED = """
        SELECT a.id, a.name, a.is_active
          FROM app.manager_areas ma
          JOIN app.areas a ON a.id = ma.area_id
         WHERE ma.manager_id = ?::uuid
         ORDER BY lower(a.name)
        """;

    @Inject
    JdbcDatabase database;

    @Override
    public List<ManagedArea> listManagedAreas(
        UUID managerId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> readManaged(sql, managerId));
    }

    @Override
    public List<ManagedArea> replaceManagedAreas(
        UUID managerId,
        UUID actorId,
        List<UUID> areaIds,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> {
            sql.run("DELETE FROM app.manager_areas WHERE manager_id = ?::uuid", managerId);

            for (UUID areaId : areaIds) {
                Optional<Integer> inserted = sql.queryOne("""
                    INSERT INTO app.manager_areas (manager_id, area_id, assigned_by)
                    SELECT ?::uuid, ?::uuid, ?::uuid
                     WHERE EXISTS (
                         SELECT 1 FROM app.areas WHERE id = ?::uuid AND is_active
                     )
                    RETURNING 1
                    """, rows -> rows.getInt(1), managerId, areaId, actorId, areaId);

                if (inserted.isEmpty()) {
                    throw ApiException.badRequest("Alguna de las áreas no existe o está inactiva.");
                }
            }

            return readManaged(sql, managerId);
        });
    }

    private static List<ManagedArea> readManaged(SqlExecutor sql, UUID managerId) {
        return sql.queryMany(SELECT_MANAGED, PostgresManagedAreasAdapter::mapArea, managerId);
    }

    private static ManagedArea mapArea(ResultSet rows) throws SQLException {
        return new ManagedArea(
            rows.getObject("id", UUID.class),
            rows.getString("name"),
            rows.getBoolean("is_active")
        );
    }
}
