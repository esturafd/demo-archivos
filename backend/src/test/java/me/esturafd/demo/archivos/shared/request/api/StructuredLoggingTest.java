package me.esturafd.demo.archivos.shared.request.api;

import io.quarkus.test.junit.QuarkusTest;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Pruebas de los registros estructurados.
 *
 * <p>El registro se escribe también a un archivo en JSON (solo en las pruebas),
 * para poder comprobar de verdad el formato y que no queda ninguna credencial.
 */
@QuarkusTest
class StructuredLoggingTest extends ApiTestBase {

    @Test
    void cadaPeticionDejaSuRequestIdEnElLogEnJson() throws Exception {
        String requestId = "prueba-" + UUID.randomUUID();

        given()
            .header("X-Request-Id", requestId)
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);

        String line = esperarLineaCon(requestId);

        // Es una línea JSON con el identificador como campo de nivel raíz.
        assertThat(line.trim(), org.hamcrest.Matchers.startsWith("{"));
        assertThat(line, containsString("\"requestId\":\"" + requestId + "\""));
        assertThat(line, containsString("\"level\""));
        // Plano, no anidado bajo "mdc".
        assertThat(line, not(containsString("\"mdc\":{\"requestId\"")));
    }

    @Test
    void lasCredencialesNoQuedanEnElLog() throws Exception {
        login(ACTIVE_EMAIL);

        // Hay una línea para la propia petición de inicio de sesión...
        String linea = esperarLineaCon("api/auth/sessions");
        assertThat(linea, not(org.hamcrest.Matchers.emptyOrNullString()));

        // ...pero la contraseña no aparece en ningún sitio.
        String contenido = leerLog();
        assertThat(contenido, not(containsString(PASSWORD)));
        assertThat(contenido, not(containsString("password_hash")));
        assertThat(contenido, not(containsString("token_hash")));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static Path rutaLog() {
        return Path.of(ConfigProvider.getConfig().getValue("quarkus.log.file.path", String.class));
    }

    private static String leerLog() throws Exception {
        Path path = rutaLog();
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    /** Espera a que el manejador de archivo vuelque una línea que contenga el texto. */
    private static String esperarLineaCon(String needle) throws Exception {
        for (int intento = 0; intento < 100; intento++) {
            for (String line : leerLog().lines().toList()) {
                if (line.contains(needle)) {
                    return line;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No apareció en el registro: " + needle);
    }
}
