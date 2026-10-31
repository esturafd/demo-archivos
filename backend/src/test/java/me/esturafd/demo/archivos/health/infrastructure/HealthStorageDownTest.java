package me.esturafd.demo.archivos.health.infrastructure;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Estado de salud con el almacenamiento caído.
 *
 * <p>La disponibilidad pasa a 503 y la vida sigue en 200.
 */
@QuarkusTest
@TestProfile(StorageDownProfile.class)
class HealthStorageDownTest {

    @Test
    void readyInformaQueElAlmacenamientoNoResponde() {
        given()
            .when().get("/health/ready")
            .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body(containsString("storage"));
    }

    @Test
    void liveSigueRespondiendo() {
        given()
            .when().get("/health/live")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
