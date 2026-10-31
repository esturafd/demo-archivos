package me.esturafd.demo.archivos.health.infrastructure;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Estado de salud con todo en orden.
 *
 * <p>La base de datos es real: Quarkus levanta una instancia desechable para las pruebas. El
 * almacenamiento se sustituye por un doble, porque no hay un servicio de objetos en el entorno
 * de pruebas.
 */
@QuarkusTest
class HealthEndpointsTest {

    @Test
    void liveRespondeSinDependencias() {
        given()
            .when().get("/health/live")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void readyCompruebaLasDependencias() {
        given()
            .when().get("/health/ready")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.size()", is(2))
                .body("checks.name", org.hamcrest.Matchers.hasItems("database", "storage"));
    }

    @Test
    void laRespuestaDeSaludNoRevelaDetallesInternos() {
        String body = given()
            .when().get("/health/ready")
            .then().statusCode(200).extract().body().asString();

        // Ni credenciales, ni cadenas de conexión, ni versiones del motor.
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("password")));
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("jdbc")));
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("postgres")));
        org.hamcrest.MatcherAssert.assertThat(body, not(containsString("localhost")));
    }
}
