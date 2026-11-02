package me.esturafd.demo.archivos.e2e;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Recorrido corto contra el artefacto empaquetado, pensado para correr también
 * sobre la imagen nativa.
 *
 * <p>Con {@code ./gradlew quarkusIntTest} se ejecuta sobre el jar; con
 * {@code ./gradlew nativeTest}, sobre el binario nativo (en Linux; en macOS el
 * binario es de Linux). Comprueba lo que suele romperse al pasar a nativo: el
 * arranque y la salud, el acceso a datos con RLS, la verificación de la
 * contraseña (bcrypt) y la emisión y resolución de la cookie de sesión.
 *
 * <p>Reutiliza la siembra de {@link ApiTestBase}, que aplica el mismo esquema que
 * producción sobre la base que levanta Dev Services.
 */
@QuarkusIntegrationTest
class NativeSmokeIT extends ApiTestBase {

    @Test
    void arrancaConSaludYCompletaElRecorridoCorto() {
        // Arranque y salud.
        given().when().get("/health/live")
            .then().statusCode(200).body("status", is("UP"));

        // Acceso a datos + bcrypt + cookie de sesión: iniciar y resolver la sesión.
        Client client = login(ACTIVE_EMAIL);

        given().cookie("session", client.session())
            .when().get("/api/auth/sessions/current")
            .then().statusCode(200).body("user.email", is(ACTIVE_EMAIL));
    }
}
