package me.esturafd.demo.archivos.shared.infrastructure.postgres;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.application.data.DataMediator;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Implementación del mediador de datos sobre PostgreSQL.
 *
 * <p>Es la única clase que conoce la conexión y el SQL. Aquí vivirán también, en las siguientes
 * fases, la transacción y el contexto de autorización de la base de datos.
 *
 * <p>Trabaja sobre JDBC y SQL estándar, sin usar nada específico del proveedor actual.
 */
@ApplicationScoped
public class PostgresDataMediator implements DataMediator {

    private static final Logger LOG = Logger.getLogger(PostgresDataMediator.class);

    @Inject
    AgroalDataSource dataSource;

    @Override
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
}
