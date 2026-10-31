package me.esturafd.demo.archivos.files.infrastructure;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import me.esturafd.demo.archivos.files.application.FilesStoragePort;
import me.esturafd.demo.archivos.shared.error.application.ApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sustituto del almacenamiento de objetos para las pruebas.
 *
 * <p>Guarda los objetos en memoria, así las pruebas de la API no dependen de un
 * servicio S3 real. Expone el mapa para poder comprobar, por ejemplo, que la
 * compensación dejó el bucket limpio.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestFilesStoragePort implements FilesStoragePort {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void store(String key, byte[] content, String contentType) {
        objects.put(key, content);
    }

    @Override
    public InputStream retrieve(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw ApiException.serviceUnavailable(null);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    public int size() {
        return objects.size();
    }

    public void clear() {
        objects.clear();
    }
}
