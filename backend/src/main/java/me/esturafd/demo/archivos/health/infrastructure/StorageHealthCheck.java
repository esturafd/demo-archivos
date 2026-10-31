package me.esturafd.demo.archivos.health.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.application.storage.StorageMediator;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Comprueba que el almacenamiento de objetos responde y que el bucket es accesible.
 *
 * <p>Consulta al mediador de almacenamiento, no al SDK del proveedor.
 */
@Readiness
@ApplicationScoped
public class StorageHealthCheck implements HealthCheck {

    static final String NAME = "storage";

    @Inject
    StorageMediator storageMediator;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named(NAME)
            .status(storageMediator.isAvailable())
            .build();
    }
}
