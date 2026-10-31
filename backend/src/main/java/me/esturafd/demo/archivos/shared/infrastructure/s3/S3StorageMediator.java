package me.esturafd.demo.archivos.shared.infrastructure.s3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.application.storage.StorageMediator;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Implementación del mediador de almacenamiento sobre la API S3.
 *
 * <p>Es la única clase que conoce el endpoint, el bucket y las credenciales. Hablar S3 en lugar
 * del SDK de un proveedor concreto permite cambiar de servicio sin tocar los casos de uso: el
 * proveedor actual expone la API S3.
 */
@ApplicationScoped
public class S3StorageMediator implements StorageMediator {

    private static final Logger LOG = Logger.getLogger(S3StorageMediator.class);

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
