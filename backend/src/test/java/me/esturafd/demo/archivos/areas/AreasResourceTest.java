package me.esturafd.demo.archivos.areas;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
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

/**
 * Pruebas de la API de áreas.
 *
 * <p>Comprueba la guarda de administrador, el nombre único sin distinguir
 * mayúsculas, el alcance de lectura por RLS, y que desactivar un área no borra
 * nada pero impide nuevas cargas.
 */
@QuarkusTest
class AreasResourceTest extends ApiTestBase {

    private static final String TEXT_TYPE = "text/plain";
    private static final byte[] CONTENT = "contenido".getBytes(StandardCharsets.UTF_8);

    // ------------------------------------------------------------------
    // Alta y unicidad
    // ------------------------------------------------------------------

    @Test
    void elAdministradorCreaYListaAreas() {
        Client admin = login(ADMIN_EMAIL);
        String id = createArea(admin, "Nueva Área", "Una descripción")
            .statusCode(201)
            .extract().jsonPath().getString("id");

        assertThat(id, not(emptyOrNullString()));
        assertThat(areaNames(list(admin)), hasItem("Nueva Área"));
    }

    @Test
    void unNombreDuplicadoConDistintaCapitalizacionResponde409() {
        Client admin = login(ADMIN_EMAIL);
        createArea(admin, "Soporte", null).statusCode(201);

        createArea(admin, "SOPORTE", null)
            .statusCode(409)
            .body("code", is("conflict"));
    }

    // ------------------------------------------------------------------
    // Autorización
    // ------------------------------------------------------------------

    @Test
    void soloElAdministradorEscribe() {
        Client active = login(ACTIVE_EMAIL);
        createArea(active, "No permitida", null).statusCode(403);
        patchArea(active, AREA_A, Map.of("name", "Renombrada")).statusCode(403);
    }

    @Test
    void elEstandarVeSuAreaYElAdministradorTodas() {
        Client active = login(ACTIVE_EMAIL);
        Client other = login(OTHER_EMAIL);
        Client admin = login(ADMIN_EMAIL);

        assertThat(areaNames(list(active)), is(List.of("Area A")));
        assertThat(areaNames(list(other)), is(List.of("Area B")));
        assertThat(list(admin), hasSize(2));
    }

    @Test
    void elGerenteVeLasAreasQueGestiona() {
        Client manager = login(MANAGER_EMAIL);
        assertThat(areaNames(list(manager)), is(List.of("Area A")));
    }

    // ------------------------------------------------------------------
    // Edición y desactivación
    // ------------------------------------------------------------------

    @Test
    void editaYDesactivaUnArea() {
        Client admin = login(ADMIN_EMAIL);
        String id = createArea(admin, "Editable", "inicial")
            .statusCode(201).extract().jsonPath().getString("id");

        patchArea(admin, id, Map.of("name", "Editada", "description", "cambiada"))
            .statusCode(200)
            .body("name", is("Editada"))
            .body("description", is("cambiada"))
            .body("active", is(true));

        patchArea(admin, id, Map.of("active", false))
            .statusCode(200)
            .body("active", is(false));
    }

    @Test
    void editarUnaAreaInexistenteResponde404() {
        Client admin = login(ADMIN_EMAIL);
        patchArea(admin, "00000000-0000-0000-0000-000000000000", Map.of("active", false))
            .statusCode(404);
    }

    @Test
    void desactivarUnAreaNoEliminaSusArchivosYBloqueaNuevasCargas() {
        Client active = login(ACTIVE_EMAIL);
        uploadFile(active, "en-area-a.txt");
        Client admin = login(ADMIN_EMAIL);

        patchArea(admin, AREA_A, Map.of("active", false))
            .statusCode(200)
            .body("active", is(false));

        // El archivo y el área siguen existiendo; el área solo queda inactiva.
        assertThat(fileNames(active), hasItem("en-area-a.txt"));
        given().cookie("session", admin.session())
            .when().get("/api/areas/" + AREA_A)
            .then().statusCode(200).body("active", is(false));

        // Pero ya no admite cargas nuevas.
        postFile(active, "nuevo.txt").statusCode(403);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static ValidatableResponse createArea(Client client, String name, String description) {
        Map<String, Object> body = description == null
            ? Map.of("name", name)
            : Map.of("name", name, "description", description);
        return authenticated(client)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/areas")
            .then();
    }

    private static ValidatableResponse patchArea(Client client, String id, Map<String, Object> body) {
        return authenticated(client)
            .contentType(ContentType.JSON)
            .body(body)
            .when().patch("/api/areas/" + id)
            .then();
    }

    private static List<Map<String, Object>> list(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/areas")
            .then().statusCode(200)
            .extract().as(new TypeRef<List<Map<String, Object>>>() {
            });
    }

    private static List<String> areaNames(List<Map<String, Object>> areas) {
        return areas.stream().map(area -> (String) area.get("name")).toList();
    }

    private static String uploadFile(Client client, String name) {
        return authenticated(client)
            .multiPart("file", name, CONTENT, TEXT_TYPE)
            .when().post("/api/files")
            .then().statusCode(201).extract().jsonPath().getString("id");
    }

    private static ValidatableResponse postFile(Client client, String name) {
        return authenticated(client)
            .multiPart("file", name, CONTENT, TEXT_TYPE)
            .when().post("/api/files")
            .then();
    }

    private static List<String> fileNames(Client client) {
        List<Map<String, Object>> items = given().cookie("session", client.session())
            .when().get("/api/files")
            .then().statusCode(200)
            .extract().jsonPath().getList("items");
        return items.stream().map(file -> (String) file.get("originalName")).toList();
    }
}
