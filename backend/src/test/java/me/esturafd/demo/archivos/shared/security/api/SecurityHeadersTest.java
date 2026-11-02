package me.esturafd.demo.archivos.shared.security.api;

import io.quarkus.test.junit.QuarkusTest;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Pruebas de las cabeceras de seguridad.
 *
 * <p>El filtro es transversal, así que se comprueba sobre varios tipos de
 * respuesta: una denegada, una de negocio autenticada y una de infraestructura.
 */
@QuarkusTest
class SecurityHeadersTest extends ApiTestBase {

    @Test
    void todaRespuestaDeLaApiLlevaCabecerasDeEndurecimiento() {
        // Sin sesión: una respuesta de error también debe llevarlas.
        given()
            .when().get("/api/auth/sessions/current")
            .then()
                .statusCode(401)
                .header("X-Content-Type-Options", is("nosniff"))
                .header("X-Frame-Options", is("DENY"))
                .header("Referrer-Policy", is("no-referrer"))
                .header("Content-Security-Policy", is("default-src 'none'"));
    }

    @Test
    void losDatosDeLaApiNoSeCachean() {
        Client client = login(ACTIVE_EMAIL);
        authenticated(client)
            .when().get("/api/files")
            .then()
                .statusCode(200)
                .header("Cache-Control", is("no-store"));

        // También en las respuestas públicas de la API.
        given()
            .when().get("/api/auth/sessions/current")
            .then()
                .statusCode(401)
                .header("Cache-Control", is("no-store"));
    }

    @Test
    void hstsApareceSoloBajoTls() {
        // En claro (HTTP) no se emite.
        given()
            .when().get("/api/auth/sessions/current")
            .then()
                .statusCode(401)
                .header("Strict-Transport-Security", nullValue());

        // Detrás de un proxy que terminó el TLS, sí.
        given()
            .header("X-Forwarded-Proto", "https")
            .when().get("/api/auth/sessions/current")
            .then()
                .statusCode(401)
                .header("Strict-Transport-Security", containsString("max-age=31536000"));
    }

    @Test
    void elNoCacheSoloSeAplicaALaApiDeNegocio() {
        // La barra inicial varía según la versión; el filtro la normaliza.
        assertThat(SecurityHeadersFilter.isBusinessPath("/api/files"), is(true));
        assertThat(SecurityHeadersFilter.isBusinessPath("api/files"), is(true));
        assertThat(SecurityHeadersFilter.isBusinessPath("/api/auth/sessions/current"), is(true));

        assertThat(SecurityHeadersFilter.isBusinessPath("/health/live"), is(false));
        assertThat(SecurityHeadersFilter.isBusinessPath("q/openapi"), is(false));
        assertThat(SecurityHeadersFilter.isBusinessPath(null), is(false));
    }
}
