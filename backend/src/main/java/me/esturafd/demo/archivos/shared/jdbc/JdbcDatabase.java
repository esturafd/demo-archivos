package me.esturafd.demo.archivos.shared.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.error.application.ApiException;

/**
 * Abre conexiones y transacciones.
 *
 * <p>Es el único punto que conoce la conexión y el contexto de autorización. Al
 * abrir una transacción fija {@code app.set_context} y confirma o revierte; así
 * el contexto es local a la transacción y no se filtra entre peticiones que
 * reutilicen una conexión del pool.
 *
 * <p>El código de las operaciones recibe un {@link SqlExecutor}, nunca una
 * conexión.
 */
@ApplicationScoped
public class JdbcDatabase {

    private static final Logger LOG = Logger.getLogger(JdbcDatabase.class);

    @Inject
    AgroalDataSource dataSource;

    /**
     * Rol bajo el que corren las transacciones. Vacío significa no cambiar de
     * rol. Se usa para que la transacción quede sujeta a RLS aunque la conexión
     * use un rol privilegiado: en producción la conexión ya es este rol, y en
     * las pruebas se fija desde la configuración.
     */
    @ConfigProperty(name = "app.database.runtime-role", defaultValue = "")
    String runtimeRole;

    /** Comprueba que el proveedor responde, sin lanzar excepciones. */
    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("select 1");
            return true;
        } catch (Exception failure) {
            // El detalle queda en el registro; la respuesta de salud no lo incluye.
            LOG.warnf("La base de datos no responde: %s", failure.getMessage());
            return false;
        }
    }

    /**
     * Ejecuta el trabajo dentro de una transacción con el contexto fijado.
     *
     * @param userId    usuario del contexto; {@code null} solo antes del login
     * @param requestId identificador de la petición, obligatorio
     * @param ipAddress IP de origen, obligatoria
     */
    public <T> T inTransaction(UUID userId, String requestId, String ipAddress, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            SqlExecutor sql = new SqlExecutor(connection);
            applyRuntimeRole(sql);
            sql.run("SELECT app.set_context(?::uuid, ?, ?::inet)", userId, requestId, ipAddress);
            try {
                T result = work.run(sql);
                connection.commit();
                return result;
            } catch (Exception failure) {
                rollback(connection);
                throw failure;
            }
        } catch (ApiException apiFailure) {
            throw apiFailure;
        } catch (Exception failure) {
            LOG.errorf(failure, "Fallo en la operación de datos");
            throw translate(failure);
        }
    }

    /** Ejecuta el trabajo sin transacción explícita (una sola sentencia). */
    public <T> T inConnection(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(new SqlExecutor(connection));
        } catch (ApiException apiFailure) {
            throw apiFailure;
        } catch (Exception failure) {
            LOG.errorf(failure, "Fallo en la operación de datos");
            throw translate(failure);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            LOG.warnf("No se pudo revertir la transacción: %s", rollbackFailure.getMessage());
        }
    }

    /**
     * Cambia al rol de ejecución dentro de la transacción. El nombre no puede
     * ir como parámetro enlazado, así que se valida su forma antes de usarlo.
     */
    private void applyRuntimeRole(SqlExecutor sql) {
        if (runtimeRole == null || runtimeRole.isBlank()) {
            return;
        }
        if (!runtimeRole.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalStateException("Nombre de rol de ejecución inválido: " + runtimeRole);
        }
        sql.run("SET LOCAL ROLE " + runtimeRole);
    }

    /**
     * Traduce un fallo de la base a la respuesta que corresponde: una violación
     * de unicidad es un conflicto (409) y una denegación de permisos o política
     * es un acceso prohibido (403). El resto son errores internos.
     */
    private static ApiException translate(Exception failure) {
        SQLException sql = sqlException(failure);
        if (sql == null) {
            return ApiException.internalError(failure);
        }
        String state = sql.getSQLState();
        if ("23505".equals(state)) {
            return ApiException.conflict(null);
        }
        if ("23514".equals(state)) {
            return ApiException.badRequest(null);
        }
        if ("42501".equals(state)) {
            return ApiException.forbidden();
        }
        return ApiException.internalError(failure);
    }

    private static SQLException sqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }
}
