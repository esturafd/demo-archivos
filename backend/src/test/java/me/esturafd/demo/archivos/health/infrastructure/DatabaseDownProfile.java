package me.esturafd.demo.archivos.health.infrastructure;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Perfil con una base de datos inalcanzable.
 *
 * <p>Al indicar una URL, Quarkus no levanta la instancia desechable y la conexión falla. Es la
 * forma de comprobar el escenario «base caída» sin detener nada.
 */
public class DatabaseDownProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/no-existe?connectTimeout=2",
            "quarkus.datasource.username", "nadie",
            "quarkus.datasource.password", "nada",
            // Acota la espera para que la comprobación no se quede colgada.
            "quarkus.datasource.jdbc.acquisition-timeout", "3S"
        );
    }
}
