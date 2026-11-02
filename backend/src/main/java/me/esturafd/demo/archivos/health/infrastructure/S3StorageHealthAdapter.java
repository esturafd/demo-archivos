package me.esturafd.demo.archivos.health.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.health.application.StorageHealthPort;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Comprueba el almacenamiento con una petición mínima al bucket.
 *
 * <p>Es el adaptador propio de `health`: no dice nada sobre subir o descargar
 * objetos, solo si el proveedor responde.
 */
@ApplicationScoped
public class S3StorageHealthAdapter implements StorageHealthPort {

    private static final Logger LOG = Logger.getLogger(S3StorageHealthAdapter.class);

    @Inject
    S3Client s3;

    @ConfigProperty(name = "app.storage.bucket")
    String bucket;

    @Override
    public boolean isAvailable() {
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return true;
        } catch (Exception failure) {
            // El detalle queda en el registro; la respuesta de salud no lo incluye.
            LOG.warnf("El almacenamiento no responde: %s", failure.getMessage());
            return false;
        }
    }
}
