package me.esturafd.demo.archivos.audit;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Pruebas del historial.
 *
 * <p>Los eventos se generan usando la propia API (inicios de sesión, cargas,
 * descargas), y luego se consulta el historial con cada rol.
 */
@QuarkusTest
class AuditResourceTest extends ApiTestBase {

    private static final String TEXT_TYPE = "text/plain";
    private static final byte[] CONTENT = "contenido".getBytes(StandardCharsets.UTF_8);

    @Test
    void elAdministradorVeTodoElHistorial() {
        login(ACTIVE_EMAIL);
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "auditado.txt");

        Client admin = login(ADMIN_EMAIL);
        List<Map<String, Object>> items = items(list(admin));
        assertThat(actions(items), hasItem("login_succeeded"));
        assertThat(actions(items), hasItem("file_uploaded"));
    }

    @Test
    void elGerenteVeSusAccionesYLasDeSusAreas() {
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "en-area-a.txt");
        Client other = login(OTHER_EMAIL);
        uploadFile(other, "en-area-b.txt");

        Client manager = login(MANAGER_EMAIL);
        List<Map<String, Object>> items = items(list(manager));

        List<String> emails = items.stream().map(item -> (String) item.get("actorEmail")).toList();
        // Ve la carga del estándar de su área.
        assertThat(emails, hasItem(ACTIVE_EMAIL));
        // No ve la del estándar de otra área.
        assertThat(emails, not(hasItem(OTHER_EMAIL)));
        // Y ve sus propias acciones.
        assertThat(emails, hasItem(MANAGER_EMAIL));
    }

    @Test
    void elEstandarYElJefeNoTienenAccesoAlHistorial() {
        Client active = login(ACTIVE_EMAIL);
        Client head = login(HEAD_EMAIL);

        // La guarda de rol rechaza antes de llegar a la base.
        given().cookie("session", active.session())
            .when().get("/api/audit/events").then().statusCode(403);
        given().cookie("session", head.session())
            .when().get("/api/audit/events").then().statusCode(403);
    }

    @Test
    void filtraPorAccionYPorActor() {
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "filtro.txt");

        Client admin = login(ADMIN_EMAIL);

        List<Map<String, Object>> uploads = items(list(admin, Map.of("action", "file_uploaded")));
        assertThat(uploads, not(hasSize(0)));
        assertThat(actions(uploads), not(hasItem("login_succeeded")));

        List<Map<String, Object>> byActor = items(list(admin, Map.of("actorId", ACTIVE)));
        assertThat(byActor, not(hasSize(0)));
        assertThat(
            byActor.stream().map(item -> (String) item.get("actorEmail")).distinct().toList(),
            is(List.of(ACTIVE_EMAIL)));
    }

    @Test
    void paginaPorCursor() {
        login(ACTIVE_EMAIL);
        login(OTHER_EMAIL);

        Client admin = login(ADMIN_EMAIL);
        ValidatableResponse first = list(admin, Map.of("limit", 1)).statusCode(200)
            .body("items.size()", is(1));

        String cursor = first.extract().jsonPath().getString("nextCursor");
        assertThat(cursor, not(emptyOrNullString()));

        ValidatableResponse second = list(admin, Map.of("limit", 1, "cursor", cursor)).statusCode(200)
            .body("items.size()", is(1));

        long firstId = first.extract().jsonPath().getLong("items[0].id");
        long secondId = second.extract().jsonPath().getLong("items[0].id");
        assertThat(secondId, not(is(firstId)));
    }

    @Test
    void elDetalleFueraDeAlcanceResponde404() {
        // Un evento del área B, que el gerente de A no gestiona.
        Client other = login(OTHER_EMAIL);
        uploadFile(other, "de-otra-area.txt");
        Client admin = login(ADMIN_EMAIL);

        long eventId = items(list(admin, Map.of("action", "file_uploaded"))).get(0).get("id")
            instanceof Number number ? number.longValue() : 0L;

        Client manager = login(MANAGER_EMAIL);
        given().cookie("session", manager.session())
            .when().get("/api/audit/events/" + eventId)
            .then().statusCode(404);
    }

    @Test
    void elHistorialConservaLosDatosDelEvento() {
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "con-datos.txt");
        Client admin = login(ADMIN_EMAIL);

        List<Map<String, Object>> items = items(list(admin, Map.of("action", "file_uploaded")));
        Map<String, Object> event = items.get(0);

        assertThat(event.get("occurredAt"), not(nullValue()));
        assertThat((String) event.get("action"), is("file_uploaded"));
        assertThat(event.get("actorEmail"), not(nullValue()));
        assertThat((String) event.get("fileName"), is("con-datos.txt"));
        assertThat(event.get("fileId"), not(nullValue()));
        assertThat(event.get("ipAddress"), not(nullValue()));
        assertThat((String) event.get("requestId"), not(emptyOrNullString()));
    }

    @Test
    void elHistorialSigueLegibleTrasCambiarDeArea() {
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "antes-del-cambio.txt");

        // El administrador mueve al estándar a otra área.
        Client admin = login(ADMIN_EMAIL);
        authenticated(admin)
            .contentType(io.restassured.http.ContentType.JSON)
            .body(Map.of("role", "standard", "areaId", AREA_B))
            .when().patch("/api/users/" + ACTIVE)
            .then().statusCode(200);

        // El evento conserva el área que el actor tenía al ejecutar la acción.
        List<Map<String, Object>> items = items(list(admin, Map.of("action", "file_uploaded")));
        Map<String, Object> event = items.get(0);
        assertThat((String) event.get("actorAreaName"), is("Area A"));
        assertThat((String) event.get("fileName"), is("antes-del-cambio.txt"));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static ValidatableResponse list(Client client) {
        return list(client, Map.of());
    }

    private static ValidatableResponse list(Client client, Map<String, Object> params) {
        RequestSpecification request = given().cookie("session", client.session());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            request = request.queryParam(entry.getKey(), entry.getValue());
        }
        return request.when().get("/api/audit/events").then().statusCode(200);
    }

    private static List<Map<String, Object>> items(ValidatableResponse response) {
        return response.extract().jsonPath().getList("items");
    }

    private static List<String> actions(List<Map<String, Object>> items) {
        return items.stream().map(item -> (String) item.get("action")).toList();
    }

    private static void uploadFile(Client client, String name) {
        authenticated(client)
            .multiPart("file", name, CONTENT, TEXT_TYPE)
            .when().post("/api/files")
            .then().statusCode(201);
    }
}
