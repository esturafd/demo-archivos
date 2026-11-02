package me.esturafd.demo.archivos.files.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.FilesDataPort;
import me.esturafd.demo.archivos.files.application.model.AccessIntent;
import me.esturafd.demo.archivos.files.application.model.FileForDownload;
import me.esturafd.demo.archivos.files.application.model.FileOwner;
import me.esturafd.demo.archivos.files.application.model.FileSearch;
import me.esturafd.demo.archivos.files.application.model.NewFile;
import me.esturafd.demo.archivos.files.application.model.StoredFile;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.jdbc.JdbcDatabase;
import me.esturafd.demo.archivos.shared.jdbc.SqlExecutor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de datos de `files` sobre PostgreSQL.
 *
 * <p>Es el único que conoce el SQL de los archivos. El alcance lo aplican las
 * políticas RLS: aquí no se repite la matriz de permisos. Cada operación fija el
 * contexto con el actor y reutiliza las funciones de autorización de la base.
 */
@ApplicationScoped
public class PostgresFilesAdapter implements FilesDataPort {

    private static final String METADATA_COLUMNS =
        "id, original_name, mime_type, size_bytes, owner_id, owner_email, area_id, uploaded_at";

    @Inject
    JdbcDatabase database;

    @Override
    public List<StoredFile> listVisibleFiles(
        FileSearch search,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT %s
              FROM app.files
             WHERE deleted_at IS NULL
               AND (?::text IS NULL OR original_name ILIKE '%%' || ?::text || '%%')
               AND (?::uuid IS NULL OR owner_id = ?::uuid)
               AND (?::timestamptz IS NULL OR (uploaded_at, id) < (?::timestamptz, ?::uuid))
             ORDER BY uploaded_at DESC, id DESC
             LIMIT ?
            """.formatted(METADATA_COLUMNS),
            PostgresFilesAdapter::mapStoredFile,
            search.text(), search.text(),
            search.ownerId(), search.ownerId(),
            timestamp(search.cursorUploadedAt()), timestamp(search.cursorUploadedAt()), search.cursorId(),
            search.limit() + 1
        ));
    }

    @Override
    public Optional<StoredFile> findVisibleFile(
        UUID fileId,
        UUID actorId,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryOne("""
            SELECT %s
              FROM app.files
             WHERE id = ?::uuid
               AND deleted_at IS NULL
            """.formatted(METADATA_COLUMNS),
            PostgresFilesAdapter::mapStoredFile,
            fileId
        ));
    }

    @Override
    public List<FileOwner> listVisibleOwners(UUID actorId, String requestId, String ipAddress) {
        // Los propietarios salen de los archivos que el actor puede ver (RLS). El
        // nombre se toma de app.users, con su propio RLS: para quien puede ver el
        // archivo, el dueño también es visible; si no, el nombre queda nulo y se
        // usa el correo.
        return database.inTransaction(actorId, requestId, ipAddress, sql -> sql.queryMany("""
            SELECT DISTINCT f.owner_id, f.owner_email, u.name AS owner_name
              FROM app.files f
              LEFT JOIN app.users u ON u.id = f.owner_id
             WHERE f.deleted_at IS NULL
            """, PostgresFilesAdapter::mapFileOwner));
    }

    @Override
    public Optional<FileForDownload> openForDownload(
        UUID fileId,
        UUID actorId,
        String actorEmail,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> {
            Optional<FileForDownload> found = sql.queryOne("""
                SELECT id, storage_key, original_name, mime_type, size_bytes
                  FROM app.files
                 WHERE id = ?::uuid
                   AND deleted_at IS NULL
                """, PostgresFilesAdapter::mapFileForDownload, fileId);

            found.ifPresent(file -> appendEvent(
                sql, "file_downloaded", actorId, actorEmail, file.id(), file.originalName(),
                "{\"bytes\":" + file.sizeBytes() + "}"));

            return found;
        });
    }

    @Override
    public StoredFile insertFile(NewFile file, String requestId, String ipAddress) {
        return database.inTransaction(file.ownerId(), requestId, ipAddress, sql -> {
            StoredFile stored = sql.queryOne("""
                INSERT INTO app.files
                  (id, owner_id, owner_email, area_id, storage_key,
                   original_name, mime_type, size_bytes, sha256_hex)
                VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?)
                RETURNING %s
                """.formatted(METADATA_COLUMNS),
                PostgresFilesAdapter::mapStoredFile,
                file.id(), file.ownerId(), file.ownerEmail(), file.areaId(), file.storageKey(),
                file.originalName(), file.mimeType(), file.sizeBytes(), file.sha256Hex()
            ).orElseThrow(() -> ApiException.internalError(
                new IllegalStateException("La inserción del archivo no devolvió fila")));

            appendEvent(sql, "file_uploaded", file.ownerId(), file.ownerEmail(), file.id(),
                file.originalName(), "{\"bytes\":" + file.sizeBytes() + "}");

            return stored;
        });
    }

    @Override
    public boolean softDeleteFile(
        UUID fileId,
        UUID actorId,
        String actorEmail,
        String requestId,
        String ipAddress
    ) {
        return database.inTransaction(actorId, requestId, ipAddress, sql -> {
            Optional<String> name = sql.queryOne("""
                SELECT original_name FROM app.files
                 WHERE id = ?::uuid AND deleted_at IS NULL
                """, rows -> rows.getString(1), fileId);

            boolean deleted = sql.queryOne(
                "SELECT app.soft_delete_file(?::uuid, ?)",
                rows -> rows.getBoolean(1),
                fileId, actorEmail
            ).orElse(false);

            if (deleted) {
                appendEvent(sql, "file_deleted", actorId, actorEmail, fileId,
                    name.orElse(null), "{}");
            }

            return deleted;
        });
    }

    @Override
    public void recordAccessDenied(
        UUID fileId,
        UUID actorId,
        AccessIntent intent,
        String actorEmail,
        String requestId,
        String ipAddress
    ) {
        database.inTransaction(actorId, requestId, ipAddress, sql -> {
            appendEvent(sql, "access_denied", actorId, actorEmail, fileId, null,
                "{\"intento\":\"" + intent.metadataValue() + "\"}");
            return null;
        });
    }

    private static void appendEvent(
        SqlExecutor sql,
        String action,
        UUID actorUserId,
        String actorEmail,
        UUID fileId,
        String fileName,
        String metadata
    ) {
        sql.run("""
            SELECT audit.append_event(
                     ?::audit.audit_action, ?::uuid, ?::text, ?::uuid, ?::text, ?::jsonb)
            """, action, actorUserId, actorEmail, fileId, fileName, metadata);
    }

    private static StoredFile mapStoredFile(ResultSet rows) throws SQLException {
        return new StoredFile(
            rows.getObject("id", UUID.class),
            rows.getString("original_name"),
            rows.getString("mime_type"),
            rows.getLong("size_bytes"),
            rows.getObject("owner_id", UUID.class),
            rows.getString("owner_email"),
            rows.getObject("area_id", UUID.class),
            instant(rows, "uploaded_at")
        );
    }

    private static FileOwner mapFileOwner(ResultSet rows) throws SQLException {
        return new FileOwner(
            rows.getObject("owner_id", UUID.class),
            rows.getString("owner_email"),
            rows.getString("owner_name")
        );
    }

    private static FileForDownload mapFileForDownload(ResultSet rows) throws SQLException {
        return new FileForDownload(
            rows.getObject("id", UUID.class),
            rows.getString("storage_key"),
            rows.getString("original_name"),
            rows.getString("mime_type"),
            rows.getLong("size_bytes")
        );
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
