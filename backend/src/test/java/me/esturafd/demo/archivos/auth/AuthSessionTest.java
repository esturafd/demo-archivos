package me.esturafd.demo.archivos.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import me.esturafd.demo.archivos.support.DatabaseTestBase;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Pruebas de autenticación y sesiones.
 *
 * <p>Se ejecutan contra el esquema real, aplicado por {@link DatabaseTestBase}.
 */
@QuarkusTest
class AuthSessionTest extends DatabaseTestBase {

    // ------------------------------------------------------------------
    // Inicio de sesión
    // ------------------------------------------------------------------

    @Test
    void elLoginCorrectoEntregaCookieYNoExponeSecretos() {
        String csrf = csrfToken();
        ValidatableResponse response = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201);

        String token = response.extract().cookie("session");
        assertThat(token, not(emptyOrNullString()));

        String body = response.extract().body().asString();
        assertThat(body, not(org.hamcrest.Matchers.containsString(token)));
        assertThat(body, not(org.hamcrest.Matchers.containsString("password_hash")));
        assertThat(body, not(org.hamcrest.Matchers.containsString("token_hash")));
        assertThat(body, org.hamcrest.Matchers.containsString(ACTIVE_EMAIL));
    }

    @Test
    void losTresFallosRespondenIgual() {
        String csrf = csrfToken();

        ExtractableResponse<Response> inexistente =
            login("nadie@demo.local", PASSWORD, csrf).statusCode(401).extract();
        ExtractableResponse<Response> malaContrasena =
            login(ACTIVE_EMAIL, "incorrecta", csrf).statusCode(401).extract();
        ExtractableResponse<Response> inactivo =
            login(INACTIVE_EMAIL, PASSWORD, csrf).statusCode(401).extract();

        for (ExtractableResponse<Response> fallo : List.of(inexistente, malaContrasena, inactivo)) {
            assertThat(fallo.path("code").toString(), is("unauthenticated"));
            assertThat(fallo.cookie("session"), is(emptyOrNullString()));
        }
        String mensajeInexistente = inexistente.path("message").toString();
        String mensajeMalaContrasena = malaContrasena.path("message").toString();
        String mensajeInactivo = inactivo.path("message").toString();

        assertThat(mensajeInexistente, is(mensajeMalaContrasena));
        assertThat(mensajeMalaContrasena, is(mensajeInactivo));
    }

    @Test
    void variosIntentosFallidosProvocanTooManyRequests() {
        String csrf = csrfToken();

        for (int intento = 0; intento < 5; intento++) {
            login(ACTIVE_EMAIL, "incorrecta", csrf).statusCode(401);
        }
        login(ACTIVE_EMAIL, "incorrecta", csrf)
            .statusCode(429)
            .body("code", is("too_many_requests"));
    }

    // ------------------------------------------------------------------
    // Sesiones
    // ------------------------------------------------------------------

    @Test
    void elUsuarioDesactivadoPierdeElAccesoConSuSesionAbierta() throws Exception {
        String csrf = csrfToken();
        String token = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201)
            .extract().cookie("session");

        given().cookie("session", token)
            .when().get("/api/auth/sessions/current")
            .then().statusCode(200);

        desactivarUsuario(ACTIVE);

        given().cookie("session", token)
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);
    }

    @Test
    void elCierreDeSesionInvalidaEnElServidor() {
        String csrf = csrfToken();
        String token = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201)
            .extract().cookie("session");

        conCsrf(csrf, token)
            .when().delete("/api/auth/sessions/current")
            .then().statusCode(204);

        // Aunque alguien conservara el token, ya no vale.
        given().cookie("session", token)
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);
    }

    @Test
    void listaYRevocaSesionesPropias() {
        String csrf = csrfToken();
        String tokenA = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201)
            .extract().cookie("session");
        String tokenB = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201)
            .extract().cookie("session");

        ExtractableResponse<Response> listado = given().cookie("session", tokenA)
            .when().get("/api/auth/sessions")
            .then().statusCode(200)
            .body("sessions.size()", is(2))
            .extract();

        List<Map<String, Object>> sesiones = listado.path("sessions");
        assertThat(
            sesiones.stream().filter(s -> Boolean.TRUE.equals(s.get("current"))).count(),
            is(1L)
        );

        String otraSesion = sesiones.stream()
            .filter(s -> !Boolean.TRUE.equals(s.get("current")))
            .findFirst()
            .orElseThrow()
            .get("id")
            .toString();

        conCsrf(csrf, tokenA)
            .when().delete("/api/auth/sessions/" + otraSesion)
            .then().statusCode(204);

        given().cookie("session", tokenB)
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);
    }

    @Test
    void revocarUnaSesionInexistenteRespondeNotFound() {
        String csrf = csrfToken();
        String token = login(ACTIVE_EMAIL, PASSWORD, csrf).statusCode(201)
            .extract().cookie("session");

        // La base de datos solo permite revocar sesiones propias; una que no
        // existe (o es de otro) responde igual: 404.
        conCsrf(csrf, token)
            .when().delete("/api/auth/sessions/00000000-0000-0000-0000-000000000000")
            .then()
                .statusCode(404)
                .body("code", is("not_found"));
    }

    // ------------------------------------------------------------------
    // CSRF
    // ------------------------------------------------------------------

    @Test
    void sinTokenCsrfSeRechaza() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", ACTIVE_EMAIL, "password", PASSWORD))
            .when().post("/api/auth/sessions")
            .then()
                .statusCode(400)
                .body("code", is("bad_request"));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Obtiene la cookie CSRF, que se entrega en la primera respuesta. */
    private static String csrfToken() {
        return given()
            .when().get("/api/auth/sessions/current")
            .then().extract().cookie("csrf-token");
    }

    private static ValidatableResponse login(String email, String password, String csrf) {
        return given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", password))
            .when().post("/api/auth/sessions")
            .then();
    }

    private static io.restassured.specification.RequestSpecification conCsrf(
        String csrf, String sessionToken
    ) {
        return given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .cookie("session", sessionToken);
    }

    private static void desactivarUsuario(String userId) throws Exception {
        try (Connection connection = conectar();
             Statement statement = connection.createStatement()) {
            statement.execute(
                "UPDATE app.users SET is_active = false, deactivated_at = now() "
                    + "WHERE id = '" + userId + "'");
        }
    }
}
