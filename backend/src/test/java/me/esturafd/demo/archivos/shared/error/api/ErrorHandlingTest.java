package me.esturafd.demo.archivos.shared.error.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

/**
 * Verifica el contrato de errores de la API.
 *
 * <p>Cubre el criterio de la fase: un formato único, todos los códigos documentados, el
 * identificador de petición presente y ausencia de detalles internos.
 */
@QuarkusTest
class ErrorHandlingTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UUID_PATTERN =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @ParameterizedTest
    @CsvSource({
        "/probe/bad-request,          400, bad_request",
        "/probe/unauthenticated,      401, unauthenticated",
        "/probe/forbidden,            403, forbidden",
        "/probe/not-found,            404, not_found",
        "/probe/conflict,             409, conflict",
        "/probe/payload-too-large,    413, payload_too_large",
        "/probe/unsupported-media-type, 415, unsupported_media_type",
        "/probe/unprocessable,        422, unprocessable_content",
        "/probe/too-many-requests,    429, too_many_requests",
        "/probe/service-unavailable,  503, service_unavailable",
    })
    void cadaCodigoTieneSuEstadoYSuCuerpo(String path, int status, String code) {
        given()
            .when().get(path)
            .then()
                .statusCode(status)
                .body("code", is(code))
                .body("message", not(emptyOrNullString()))
                .body("requestId", not(emptyOrNullString()));
    }

    @Test
    void elMensajePropioSeConserva() {
        given()
            .when().get("/probe/conflict")
            .then()
                .statusCode(409)
                .body("message", is("El correo ya está registrado."));
    }

    @Test
    void elIdentificadorDeLaRespuestaCoincideConLaCabecera() {
        ExtractableResponse<Response> response = given().when().get("/probe/not-found").then().extract();

        String header = response.header(REQUEST_ID_HEADER);
        String body = response.path("requestId");

        assertThat(header, not(emptyOrNullString()));
        assertThat(body, is(header));
    }

    @Test
    void elIdentificadorSeGeneraCuandoNoLlegaNinguno() {
        String generated = given()
            .when().get("/probe/request-id")
            .then().statusCode(200).extract().body().asString();

        assertThat(generated, matchesPattern(UUID_PATTERN));
    }

    @Test
    void seRespetaElIdentificadorDelCliente() {
        given()
            .header(REQUEST_ID_HEADER, "abc-123")
            .when().get("/probe/request-id")
            .then()
                .statusCode(200)
                .body(is("abc-123"));
    }

    @Test
    void seDescartaUnIdentificadorConFormatoInvalido() {
        String returned = given()
            .header(REQUEST_ID_HEADER, "invalido!")
            .when().get("/probe/request-id")
            .then().statusCode(200).extract().body().asString();

        assertThat(returned, not(is("invalido!")));
        assertThat(returned, matchesPattern(UUID_PATTERN));
    }

    @Test
    void unErrorInesperadoNoRevelaElDetalle() {
        given()
            .when().get("/probe/boom")
            .then()
                .statusCode(500)
                .body("code", is("internal_error"))
                .body("message", is("Ocurrió un error inesperado."))
                .body("message", not(org.hamcrest.Matchers.containsString("detalle interno")));
    }

    @Test
    void unFalloDeValidacionNoRevelaLosCampos() {
        given()
            .queryParam("nombre", "ab")
            .when().get("/probe/validate")
            .then()
                .statusCode(400)
                .body("code", is("validation_error"))
                .body("message", is("La petición contiene datos inválidos."));
    }

    @Test
    void laRespuestaCorrectaNoLlevaCuerpoDeError() {
        ExtractableResponse<Response> response = given().when().get("/probe/ok").then().statusCode(200).extract();

        assertThat(response.body().asString(), is("ok"));
        assertThat(response.header(REQUEST_ID_HEADER), not(emptyOrNullString()));
    }

    @Test
    void elIdentificadorAcompanaALosRegistros() {
        String header = given()
            .when().get("/probe/mdc")
            .then().statusCode(200).extract().body().asString();

        assertThat(header, matchesPattern(UUID_PATTERN));
    }

    @Test
    void unaRutaDesconocidaTambienDevuelveElFormatoUniforme() {
        given()
            .when().get("/ruta-que-no-existe")
            .then()
                .statusCode(404)
                .body("code", is("not_found"))
                .body("message", not(emptyOrNullString()))
                .body("requestId", not(emptyOrNullString()));
    }

    @Test
    void unMetodoNoPermitidoDevuelveElFormatoUniforme() {
        // Quarkus no incluye la cabecera `Allow` al responder 405; el mapper conserva las
        // cabeceras que sí vengan en la respuesta original.
        given()
            .when().post("/probe/ok")
            .then()
                .statusCode(405)
                .body("code", is("bad_request"))
                .body("message", not(emptyOrNullString()))
                .body("requestId", not(emptyOrNullString()));
    }
}
