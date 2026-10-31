package me.esturafd.demo.archivos.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Pruebas del contrato OpenAPI.
 *
 * <p>Comprueba que el documento se genera, que cubre todos los endpoints con sus
 * códigos reales, que declara los esquemas de seguridad (cookie de sesión y token
 * CSRF) y que el arranque público (inicio de sesión) no los exige.
 *
 * <p>El documento se pide en JSON. La interfaz Swagger UI solo está en desarrollo
 * y pruebas: en producción queda desactivada por configuración.
 */
@QuarkusTest
class OpenApiDocumentTest {

    private static final String OPENAPI = "/q/openapi?format=json";

    /** Rutas y métodos que el contrato debe documentar. */
    private static final Map<String, List<String>> ENDPOINTS = Map.ofEntries(
        Map.entry("/api/auth/sessions", List.of("post", "get")),
        Map.entry("/api/auth/sessions/current", List.of("get", "delete")),
        Map.entry("/api/auth/sessions/{id}", List.of("delete")),
        Map.entry("/api/files", List.of("get", "post")),
        Map.entry("/api/files/{id}", List.of("get", "delete")),
        Map.entry("/api/files/{id}/content", List.of("get")),
        Map.entry("/api/users", List.of("get", "post")),
        Map.entry("/api/users/{id}", List.of("get", "patch")),
        Map.entry("/api/users/{id}/managed-areas", List.of("get", "put")),
        Map.entry("/api/areas", List.of("get", "post")),
        Map.entry("/api/areas/{id}", List.of("get", "patch")),
        Map.entry("/api/audit/events", List.of("get")),
        Map.entry("/api/audit/events/{id}", List.of("get"))
    );

    @Test
    void elDocumentoSeGeneraConSuInformacion() {
        String title = json().getString("info.title");
        assertThat(title, is("API de gestión de archivos"));
        assertThat(json().getString("info.version"), is("1.0.0"));
        assertThat(json().getString("info.description"), not(nullValue()));
    }

    @Test
    void cubreTodosLosEndpoints() {
        JsonPath document = json();
        for (Map.Entry<String, List<String>> entry : ENDPOINTS.entrySet()) {
            Map<String, Object> path = document.getMap("paths.'" + entry.getKey() + "'");
            assertThat("Falta la ruta " + entry.getKey(), path, notNullValue());
            for (String method : entry.getValue()) {
                assertThat(
                    "Falta " + method.toUpperCase() + " " + entry.getKey(),
                    path.containsKey(method),
                    is(true));
            }
        }
    }

    @Test
    void declaraLosEsquemasDeSeguridad() {
        JsonPath document = json();
        assertThat(document.getString("components.securitySchemes.sessionCookie.type"), is("apiKey"));
        assertThat(document.getString("components.securitySchemes.sessionCookie.in"), is("cookie"));
        assertThat(document.getString("components.securitySchemes.sessionCookie.name"), is("session"));

        assertThat(document.getString("components.securitySchemes.csrfHeader.type"), is("apiKey"));
        assertThat(document.getString("components.securitySchemes.csrfHeader.in"), is("header"));
        assertThat(document.getString("components.securitySchemes.csrfHeader.name"), is("X-CSRF-TOKEN"));
    }

    @Test
    void laSesionEsElRequisitoGlobal() {
        // El requisito global es la cookie de sesión.
        JsonPath document = json();
        assertThat(document.getList("security"), hasSize(1));
        assertThat(document.getMap("security[0]"), hasKey("sessionCookie"));
    }

    @Test
    void elInicioDeSesionEsPublico() {
        // El login no declara seguridad: es la única ruta pública.
        List<?> security = json().getList("paths.'/api/auth/sessions'.post.security");
        assertThat(security, notNullValue());
        assertThat(security, hasSize(0));
    }

    @Test
    void lasEscriturasExigenSesionYCsrf() {
        // Eliminar un archivo exige, además de la sesión, el token CSRF.
        Map<String, Object> requirement =
            json().getMap("paths.'/api/files/{id}'.delete.security[0]");
        assertThat(requirement, notNullValue());
        assertThat(requirement, hasKey("sessionCookie"));
        assertThat(requirement, hasKey("csrfHeader"));
    }

    @Test
    void declaraLosCodigosRealesDeCadaOperacion() {
        JsonPath document = json();

        // La carga declara sus errores de validación de contenido.
        assertThat(document.get("paths.'/api/files'.post.responses.'201'"), notNullValue());
        assertThat(document.get("paths.'/api/files'.post.responses.'413'"), notNullValue());
        assertThat(document.get("paths.'/api/files'.post.responses.'415'"), notNullValue());
        assertThat(document.get("paths.'/api/files'.post.responses.'422'"), notNullValue());

        // El inicio de sesión declara el límite de intentos.
        assertThat(document.get("paths.'/api/auth/sessions'.post.responses.'429'"), notNullValue());

        // Toda ruta protegida declara el 401; las administrativas, el 403.
        assertThat(document.get("paths.'/api/files'.get.responses.'401'"), notNullValue());
        assertThat(document.get("paths.'/api/users'.get.responses.'403'"), notNullValue());
        assertThat(document.get("paths.'/api/audit/events'.get.responses.'403'"), notNullValue());
    }

    @Test
    void elErrorUniformeEsUnEsquemaDocumentado() {
        JsonPath document = json();
        assertThat(document.get("components.schemas.ApiError"), notNullValue());
        assertThat(
            document.getString("components.schemas.ApiError.properties.code.description"),
            not(nullValue()));
        assertThat(
            document.getString("components.schemas.ApiError.properties.requestId.description"),
            not(nullValue()));
    }

    @Test
    void losModelosLlevanDescripcion() {
        // Los DTO se documentan con descripción y ejemplo. El documento es OpenAPI
        // 3.1, así que el ejemplo viaja en la lista `examples`.
        JsonPath document = json();
        assertThat(
            document.getString("components.schemas.FileView.properties.originalName.examples[0]"),
            is("informe.pdf"));
        assertThat(
            document.getString("components.schemas.AreaView.properties.name.description"),
            not(nullValue()));
        assertThat(
            document.getString("components.schemas.AuditEventView.properties.action.examples[0]"),
            is("file_uploaded"));
    }

    @Test
    void agrupaLasOperacionesPorEtiquetas() {
        List<String> tags = json().getList("tags.name");
        assertThat(tags, hasItem("Autenticación"));
        assertThat(tags, hasItem("Archivos"));
        assertThat(tags, hasItem("Usuarios"));
        assertThat(tags, hasItem("Áreas"));
        assertThat(tags, hasItem("Áreas gestionadas"));
        assertThat(tags, hasItem("Historial"));
    }

    // ------------------------------------------------------------------
    // Utilidad
    // ------------------------------------------------------------------

    private static JsonPath json() {
        return given()
            .when().get(OPENAPI)
            .then().statusCode(200)
            .extract().jsonPath();
    }
}
