package me.esturafd.demo.archivos.health.infrastructure;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Estado de salud con la base de datos caída.
 *
 * <p>Lo importante: la disponibilidad pasa a 503, pero la vida sigue en 200. Si la comprobación
 * de vida fallara con la base caída, el orquestador reiniciaría instancias que están sanas.
 */
@QuarkusTest
@TestProfile(DatabaseDownProfile.class)
class HealthDatabaseDownTest {

    @Test
    void readyInformaQueLaBaseNoResponde() {
        given()
            .when().get("/health/ready")
            .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body(containsString("database"));
    }

    @Test
    void liveSigueRespondiendo() {
        given()
            .when().get("/health/live")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void noRevelaDetallesDeLaConexion() {
        String body = given()
            .when().get("/health/ready")
            .then().statusCode(503).extract().body().asString();

        org.hamcrest.MatcherAssert.assertThat(body, org.hamcrest.Matchers.not(containsString("password")));
        org.hamcrest.MatcherAssert.assertThat(body, org.hamcrest.Matchers.not(containsString("127.0.0.1")));
        org.hamcrest.MatcherAssert.assertThat(body, org.hamcrest.Matchers.not(containsString("no-existe")));
    }
}
