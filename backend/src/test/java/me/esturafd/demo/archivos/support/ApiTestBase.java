package me.esturafd.demo.archivos.support;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Base de las pruebas que recorren la API autenticada.
 *
 * <p>Aporta el inicio de sesión (con su token CSRF) y una petición ya
 * autenticada, para que cada prueba se concentre en lo suyo.
 */
public abstract class ApiTestBase extends DatabaseTestBase {

    /** Cliente autenticado: su cookie CSRF y la de sesión. */
    protected record Client(String csrf, String session) {
    }

    protected static Client login(String email) {
        String csrf = given().when().get("/api/auth/sessions/current")
            .then().extract().cookie("csrf-token");

        String session = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD))
            .when().post("/api/auth/sessions")
            .then().statusCode(201)
            .extract().cookie("session");

        return new Client(csrf, session);
    }

    /** Petición que lleva la cookie de sesión y el token CSRF. */
    protected static RequestSpecification authenticated(Client client) {
        return given()
            .cookie("csrf-token", client.csrf())
            .cookie("session", client.session())
            .header("X-CSRF-TOKEN", client.csrf());
    }
}
