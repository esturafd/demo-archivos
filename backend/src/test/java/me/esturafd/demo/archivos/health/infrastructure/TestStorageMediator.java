package me.esturafd.demo.archivos.health.infrastructure;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import me.esturafd.demo.archivos.shared.application.storage.StorageMediator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sustituto del mediador de almacenamiento para las pruebas.
 *
 * <p>Sustituye al adaptador S3, así las pruebas de los endpoints de salud no dependen de un
 * servicio de objetos real. La disponibilidad se controla con una propiedad para poder probar
 * los dos escenarios.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestStorageMediator implements StorageMediator {

    @ConfigProperty(name = "app.test.storage.available", defaultValue = "true")
    boolean available;

    @Override
    public boolean isAvailable() {
        return available;
    }
}
