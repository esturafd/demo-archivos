package me.esturafd.demo.archivos.files.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.FilesStoragePort;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;

/**
 * Adaptador de almacenamiento de `files` sobre la API S3.
 *
 * <p>Es el único que conoce el bucket y el cliente S3. Un fallo del proveedor se
 * traduce en {@code 503}, sin exponer el detalle.
 */
@ApplicationScoped
public class S3FilesAdapter implements FilesStoragePort {

    private static final Logger LOG = Logger.getLogger(S3FilesAdapter.class);

    @Inject
    S3Client s3;

    @ConfigProperty(name = "app.storage.bucket")
    String bucket;

    @Override
    public void store(String key, byte[] content, String contentType) {
        try {
            s3.putObject(
                request -> request
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length),
                RequestBody.fromBytes(content)
            );
        } catch (Exception failure) {
            LOG.errorf(failure, "No se pudo guardar el objeto %s", key);
            throw ApiException.serviceUnavailable(null);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            return s3.getObject(request -> request.bucket(bucket).key(key));
        } catch (Exception failure) {
            LOG.errorf(failure, "No se pudo leer el objeto %s", key);
            throw ApiException.serviceUnavailable(null);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(request -> request.bucket(bucket).key(key));
        } catch (Exception failure) {
            LOG.errorf(failure, "No se pudo borrar el objeto %s", key);
            throw ApiException.serviceUnavailable(null);
        }
    }
}
