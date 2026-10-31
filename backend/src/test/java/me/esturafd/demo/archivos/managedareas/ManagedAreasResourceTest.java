package me.esturafd.demo.archivos.managedareas;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
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
 * Pruebas del subrecurso de áreas gestionadas.
 */
@QuarkusTest
class ManagedAreasResourceTest extends ApiTestBase {

    private static final String TEXT_TYPE = "text/plain";
    private static final byte[] CONTENT = "contenido".getBytes(StandardCharsets.UTF_8);

    @Test
    void elAdministradorReemplazaElConjuntoCompleto() {
        Client admin = login(ADMIN_EMAIL);
        assertThat(names(get(admin, MANAGER)), is(List.of("Area A")));

        put(admin, MANAGER, List.of(AREA_A, AREA_B))
            .statusCode(200)
            .body("areas.size()", is(2));

        assertThat(names(get(admin, MANAGER)), is(List.of("Area A", "Area B")));
    }

    @Test
    void unConjuntoVacioSeRechaza() {
        Client admin = login(ADMIN_EMAIL);
        put(admin, MANAGER, List.of()).statusCode(400);
    }

    @Test
    void unGerenteNoPuedeAsignarseAreasASiMismo() {
        Client manager = login(MANAGER_EMAIL);
        put(manager, MANAGER, List.of(AREA_A, AREA_B)).statusCode(403);
    }

    @Test
    void unGerenteConsultaSusAreas() {
        Client manager = login(MANAGER_EMAIL);
        assertThat(names(get(manager, MANAGER)), is(List.of("Area A")));
    }

    @Test
    void unEstandarNoVeLasDeOtroGerente() {
        Client other = login(OTHER_EMAIL);
        assertThat(get(other, MANAGER), hasSize(0));
    }

    @Test
    void unAreaInexistenteOInactivaSeRechaza() {
        Client admin = login(ADMIN_EMAIL);
        // Un identificador que no existe.
        put(admin, MANAGER, List.of("00000000-0000-0000-0000-000000000000")).statusCode(400);
        // El reemplazo fue atómico: el conjunto anterior no cambió.
        assertThat(names(get(admin, MANAGER)), is(List.of("Area A")));
    }

    @Test
    void unAreaPuedeTenerVariosGerentes() {
        Client admin = login(ADMIN_EMAIL);
        String segundo = createManager(admin, "gerente2@demo.local");

        put(admin, MANAGER, List.of(AREA_A)).statusCode(200);
        put(admin, segundo, List.of(AREA_A)).statusCode(200);

        assertThat(names(get(admin, MANAGER)), hasItem("Area A"));
        assertThat(names(get(admin, segundo)), hasItem("Area A"));
    }

    @Test
    void elNuevoAlcanceAplicaaSinIniciarSesion() {
        Client manager = login(MANAGER_EMAIL);
        Client other = login(OTHER_EMAIL);
        uploadFile(other, "en-area-b.txt");

        // El gerente solo gestiona Area A, así que no ve el archivo de Area B.
        assertThat(fileNames(manager), not(hasItem("en-area-b.txt")));

        // El administrador añade Area B al gerente.
        Client admin = login(ADMIN_EMAIL);
        put(admin, MANAGER, List.of(AREA_A, AREA_B)).statusCode(200);

        // Con la misma sesión, el gerente ya lo ve.
        assertThat(fileNames(manager), hasItem("en-area-b.txt"));
    }

    @Test
    void quitarUnAreaTambienAplicaSinIniciarSesion() {
        Client admin = login(ADMIN_EMAIL);
        put(admin, MANAGER, List.of(AREA_A, AREA_B)).statusCode(200);

        Client other = login(OTHER_EMAIL);
        uploadFile(other, "en-area-b.txt");

        Client manager = login(MANAGER_EMAIL);
        assertThat(fileNames(manager), hasItem("en-area-b.txt"));

        // El administrador le quita Area B.
        put(admin, MANAGER, List.of(AREA_A)).statusCode(200);

        // Con la misma sesión, deja de ver los archivos de Area B.
        assertThat(fileNames(manager), not(hasItem("en-area-b.txt")));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> get(Client client, String managerId) {
        return given().cookie("session", client.session())
            .when().get("/api/users/" + managerId + "/managed-areas")
            .then().statusCode(200)
            .extract().jsonPath().getList("areas");
    }

    private static ValidatableResponse put(Client client, String managerId, List<String> areaIds) {
        return authenticated(client)
            .contentType(ContentType.JSON)
            .body(Map.of("areaIds", areaIds))
            .when().put("/api/users/" + managerId + "/managed-areas")
            .then();
    }

    private static String createManager(Client admin, String email) {
        return authenticated(admin)
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD, "name", "Gerente 2", "role", "manager"))
            .when().post("/api/users")
            .then().statusCode(201)
            .extract().jsonPath().getString("id");
    }

    private static List<String> names(List<Map<String, Object>> areas) {
        return areas.stream().map(area -> (String) area.get("name")).toList();
    }

    private static void uploadFile(Client client, String name) {
        authenticated(client)
            .multiPart("file", name, CONTENT, TEXT_TYPE)
            .when().post("/api/files")
            .then().statusCode(201);
    }

    private static List<String> fileNames(Client client) {
        List<Map<String, Object>> items = given().cookie("session", client.session())
            .when().get("/api/files")
            .then().statusCode(200)
            .extract().jsonPath().getList("items");
        return items.stream().map(file -> (String) file.get("originalName")).toList();
    }
}
