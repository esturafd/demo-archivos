package me.esturafd.demo.archivos.shared.data.infrastructure;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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
            throw ApiException.internalError(failure);
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
            throw ApiException.internalError(failure);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            LOG.warnf("No se pudo revertir la transacción: %s", rollbackFailure.getMessage());
        }
    }
}
