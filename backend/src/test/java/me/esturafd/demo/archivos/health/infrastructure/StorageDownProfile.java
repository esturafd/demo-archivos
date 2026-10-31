package me.esturafd.demo.archivos.health.infrastructure;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Perfil con el almacenamiento de objetos no disponible.
 *
 * <p>La base de datos sigue siendo la real de las pruebas; lo que falla es el mediador de
 * almacenamiento.
 */
public class StorageDownProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("app.test.storage.available", "false");
    }
}
