package me.esturafd.demo.archivos.support;

import io.quarkus.elytron.security.common.BcryptUtil;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

/**
 * Base de las pruebas que necesitan la base de datos real.
 *
 * <p>Aplica al PostgreSQL de pruebas el mismo esquema que producción, leyendo
 * las migraciones del classpath (se copian desde {@code database/migrations} en
 * el build, para no duplicarlas). Después siembra un conjunto de usuarios fijo.
 *
 * <p>Se conecta con el usuario que levanta Dev Services, que es superusuario del
 * contenedor desechable; por eso puede crear roles y esquemas. El cumplimiento
 * de RLS como {@code app_runtime} lo verifica la suite de
 * {@code database/tests}, que sí se conecta con ese rol.
 */
public abstract class DatabaseTestBase {

    public static final String PASSWORD = "Correcta123";

    public static final String AREA_A = "11111111-1111-1111-1111-111111111111";
    public static final String AREA_B = "22222222-2222-2222-2222-222222222222";

    public static final String ADMIN = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5";
    public static final String ACTIVE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1";
    public static final String INACTIVE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2";
    public static final String OTHER = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3";
    public static final String HEAD = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4";

    public static final String ADMIN_EMAIL = "admin@demo.local";
    public static final String ACTIVE_EMAIL = "activo@demo.local";
    public static final String INACTIVE_EMAIL = "inactivo@demo.local";
    public static final String OTHER_EMAIL = "otro@demo.local";
    public static final String HEAD_EMAIL = "jefe@demo.local";

    private static boolean schemaReady;

    @BeforeAll
    static void prepararEsquema() throws Exception {
        if (schemaReady) {
            return;
        }
        try (Connection connection = conectar()) {
            crearRolRuntime(connection);
            if (!existeEsquema(connection)) {
                aplicarMigraciones(connection);
            }
        }
        schemaReady = true;
    }

    @BeforeEach
    void sembrarDatos() throws Exception {
        try (Connection connection = conectar();
             Statement statement = connection.createStatement()) {
            statement.execute(truncateSql());
            statement.execute(seedSql());
        }
    }

    // ------------------------------------------------------------------
    // Esquema
    // ------------------------------------------------------------------

    private static void crearRolRuntime(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                DO $$
                BEGIN
                  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
                    CREATE ROLE app_runtime NOLOGIN;
                  END IF;
                END
                $$
                """);
        }
    }

    private static boolean existeEsquema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT to_regclass('app.users') IS NOT NULL AS existe")) {
            rows.next();
            return rows.getBoolean("existe");
        }
    }

    private static void aplicarMigraciones(Connection connection) throws Exception {
        for (Path migration : archivosDeMigracion()) {
            String sql = Files.readString(migration);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private static List<Path> archivosDeMigracion() throws Exception {
        URL directory = DatabaseTestBase.class.getResource("/db/migration");
        if (directory == null) {
            throw new IllegalStateException(
                "No se encontraron migraciones en el classpath (/db/migration)");
        }
        Path path = Paths.get(directory.toURI());
        try (Stream<Path> files = Files.list(path)) {
            return files
                .filter(file -> file.getFileName().toString().endsWith(".sql"))
                .sorted()
                .toList();
        }
    }

    // ------------------------------------------------------------------
    // Datos
    // ------------------------------------------------------------------

    private static String truncateSql() {
        return """
            TRUNCATE app.areas, app.users, app.sessions, app.manager_areas, app.files,
                     app.login_rate_limits, audit.action_logs
              RESTART IDENTITY CASCADE;
            """;
    }

    private static String seedSql() {
        String hash = BcryptUtil.bcryptHash(PASSWORD);
        return """
            INSERT INTO app.areas (id, name) VALUES
              ('%s', 'Area A'),
              ('%s', 'Area B');

            INSERT INTO app.users (id, email, password_hash, name, role, area_id, is_active, deactivated_at) VALUES
              ('%s', '%s', '%s', 'Admin',      'admin',     NULL,   true,  NULL),
              ('%s', '%s', '%s', 'Activo',     'standard',  '%s',   true,  NULL),
              ('%s', '%s', '%s', 'Inactivo',   'standard',  '%s',   false, now()),
              ('%s', '%s', '%s', 'Otro',       'standard',  '%s',   true,  NULL),
              ('%s', '%s', '%s', 'Jefe',       'area_head', '%s',   true,  NULL);
            """.formatted(
            AREA_A, AREA_B,
            ADMIN, ADMIN_EMAIL, hash,
            ACTIVE, ACTIVE_EMAIL, hash, AREA_A,
            INACTIVE, INACTIVE_EMAIL, hash, AREA_A,
            OTHER, OTHER_EMAIL, hash, AREA_B,
            HEAD, HEAD_EMAIL, hash, AREA_A
        );
    }

    // ------------------------------------------------------------------
    // Conexión
    // ------------------------------------------------------------------

    protected static Connection conectar() throws SQLException {
        Config config = ConfigProvider.getConfig();
        String url = config.getValue("quarkus.datasource.jdbc.url", String.class);
        String user = config.getValue("quarkus.datasource.username", String.class);
        String password = config.getValue("quarkus.datasource.password", String.class);
        return DriverManager.getConnection(url, user, password);
    }
}
