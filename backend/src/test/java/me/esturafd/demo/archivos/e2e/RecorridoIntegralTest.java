package me.esturafd.demo.archivos.e2e;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Recorrido integral de la plataforma.
 *
 * <p>Encadena los módulos como lo haría un uso real, con los cuatro roles y las
 * dos áreas: inicio de sesión, carga, descarga, alcance entre roles, historial,
 * administración por el administrador, cambio de alcance del gerente sin volver
 * a iniciar sesión, y cierre de sesión con invalidación.
 *
 * <p>Sirve de red de seguridad antes de la imagen nativa.
 */
@QuarkusTest
class RecorridoIntegralTest extends ApiTestBase {

    private static final byte[] CONTENT = "contenido de prueba".getBytes(StandardCharsets.UTF_8);
    private static final String TEXT = "text/plain";
    private static final String NUEVO_EMAIL = "nuevo@demo.local";

    @Test
    void recorridoCompletoDeLaPlataforma() {
        // 1. Cada rol inicia sesión y obtiene su cookie CSRF.
        Client admin = login(ADMIN_EMAIL);
        Client standard = login(ACTIVE_EMAIL);
        Client head = login(HEAD_EMAIL);
        Client manager = login(MANAGER_EMAIL);
        Client other = login(OTHER_EMAIL);

        // 2. Un estándar carga un archivo, lo descarga y lo ve en su listado.
        String fileId = upload(standard, "informe.txt", CONTENT);
        assertThat(download(standard, fileId), is(CONTENT));
        assertThat(fileIds(standard), hasItem(fileId));

        // 3. Alcance de lectura por rol.
        assertThat("el gerente ve lo de su área", fileIds(manager), hasItem(fileId));
        assertThat("el jefe ve los del estándar de su área", fileIds(head), hasItem(fileId));
        assertThat("el estándar de otra área no lo ve", fileIds(other), not(hasItem(fileId)));

        // 4. Historial: el gerente ve la carga; estándar y jefe no tienen acceso.
        List<Map<String, Object>> events = auditEvents(manager, Map.of("action", "file_uploaded"));
        assertThat(actorEmails(events), hasItem(ACTIVE_EMAIL));
        auditEventsOf(standard).statusCode(403);
        auditEventsOf(head).statusCode(403);

        // 5. Rutas administrativas: estándar y jefe no entran; el gerente consulta
        //    sus áreas gestionadas, pero no las reemplaza.
        usersOf(standard).statusCode(403);
        usersOf(head).statusCode(403);
        managedAreasOf(standard, MANAGER).statusCode(403);
        managedAreasOf(manager, MANAGER).statusCode(200);
        given().cookie("session", manager.session())
            .contentType(ContentType.JSON)
            .body(Map.of("areaIds", List.of(AREA_A, AREA_B)))
            .when().put("/api/users/" + MANAGER + "/managed-areas")
            .then().statusCode(403);

        // 6. El administrador administra áreas y usuarios.
        String areaC = createArea(admin, "Área C");
        createUser(admin, NUEVO_EMAIL, PASSWORD, "standard", areaC);
        assertThat(areaNames(admin), hasItem("Área C"));

        Client nuevo = login(NUEVO_EMAIL);
        assertThat("un usuario nuevo empieza sin archivos", fileIds(nuevo), hasSize(0));

        // 7. El administrador cambia el alcance del gerente; su sesión sigue
        //    abierta y el cambio aplica en la siguiente petición.
        replaceManagedAreas(admin, MANAGER, List.of(AREA_B));
        String fileB = upload(other, "de-b.txt", CONTENT);
        assertThat("el gerente ahora ve el área B", fileIds(manager), hasItem(fileB));
        assertThat("y ya no ve el área A", fileIds(manager), not(hasItem(fileId)));

        // 8. Cierre de sesión: la cookie deja de servir.
        logout(manager);
        given().cookie("session", manager.session())
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private static String upload(Client client, String name, byte[] content) {
        return authenticated(client)
            .multiPart("file", name, content, TEXT)
            .when().post("/api/files")
            .then().statusCode(201)
            .extract().jsonPath().getString("id");
    }

    private static byte[] download(Client client, String fileId) {
        return given().cookie("session", client.session())
            .when().get("/api/files/" + fileId + "/content")
            .then().statusCode(200)
            .extract().asByteArray();
    }

    private static List<String> fileIds(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/files?limit=100")
            .then().statusCode(200)
            .extract().jsonPath().getList("items.id");
    }

    private static List<Map<String, Object>> auditEvents(Client client, Map<String, Object> params) {
        RequestSpecification request = given().cookie("session", client.session());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            request = request.queryParam(entry.getKey(), entry.getValue());
        }
        return request.when().get("/api/audit/events")
            .then().statusCode(200)
            .extract().jsonPath().getList("items");
    }

    private static List<String> actorEmails(List<Map<String, Object>> events) {
        return events.stream().map(event -> (String) event.get("actorEmail")).toList();
    }

    private static ValidatableResponse auditEventsOf(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/audit/events").then();
    }

    private static ValidatableResponse usersOf(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/users").then();
    }

    private static ValidatableResponse managedAreasOf(Client client, String userId) {
        return given().cookie("session", client.session())
            .when().get("/api/users/" + userId + "/managed-areas").then();
    }

    private static String createArea(Client admin, String name) {
        return authenticated(admin).contentType(ContentType.JSON)
            .body(Map.of("name", name))
            .when().post("/api/areas")
            .then().statusCode(201)
            .extract().jsonPath().getString("id");
    }

    private static void createUser(Client admin, String email, String password, String role, String areaId) {
        authenticated(admin).contentType(ContentType.JSON)
            .body(Map.of(
                "email", email,
                "password", password,
                "name", "Nuevo",
                "role", role,
                "areaId", areaId))
            .when().post("/api/users")
            .then().statusCode(201);
    }

    private static List<String> areaNames(Client admin) {
        return given().cookie("session", admin.session())
            .when().get("/api/areas")
            .then().statusCode(200)
            .extract().jsonPath().getList("name");
    }

    private static void replaceManagedAreas(Client admin, String managerId, List<String> areaIds) {
        authenticated(admin).contentType(ContentType.JSON)
            .body(Map.of("areaIds", areaIds))
            .when().put("/api/users/" + managerId + "/managed-areas")
            .then().statusCode(200);
    }

    private static void logout(Client client) {
        authenticated(client)
            .when().delete("/api/auth/sessions/current")
            .then().statusCode(204);
    }
}
