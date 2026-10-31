package me.esturafd.demo.archivos.health.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.application.data.DataMediator;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Comprueba que la base de datos responde.
 *
 * <p>Es una comprobación de disponibilidad, no de vida: si la base de datos falla, la instancia
 * no debe recibir tráfico, pero tampoco debe reiniciarse por ello.
 *
 * <p>Consulta al mediador de datos, no a JDBC directamente.
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    static final String NAME = "database";

    @Inject
    DataMediator dataMediator;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named(NAME)
            .status(dataMediator.isAvailable())
            .build();
    }
}
