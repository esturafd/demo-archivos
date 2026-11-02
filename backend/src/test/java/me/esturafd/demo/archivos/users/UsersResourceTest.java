package me.esturafd.demo.archivos.users;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Pruebas de la API de administración de usuarios.
 */
@QuarkusTest
class UsersResourceTest extends ApiTestBase {

    // ------------------------------------------------------------------
    // Autorización
    // ------------------------------------------------------------------

    @Test
    void soloElAdministradorAccede() {
        Client active = login(ACTIVE_EMAIL);
        given().cookie("session", active.session())
            .when().get("/api/users")
            .then().statusCode(403);
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    void creaUnUsuarioSinExponerElHash() {
        Client admin = login(ADMIN_EMAIL);
        ValidatableResponse created = createUser(
            admin, "nuevo@demo.local", PASSWORD, "Nuevo", "standard", AREA_A)
            .statusCode(201)
            .body("email", is("nuevo@demo.local"))
            .body("role", is("standard"))
            .body("areaName", is("Area A"))
            .body("active", is(true));

        String body = created.extract().body().asString();
        assertThat(body, not(containsString("password")));

        String listed = given().cookie("session", admin.session())
            .when().get("/api/users").then().statusCode(200)
            .extract().body().asString();
        assertThat(listed, not(containsString("$2a$")));
        assertThat(listed, not(containsString("password_hash")));
    }

    @Test
    void unCorreoDuplicadoSinDistinguirMayusculasResponde409() {
        Client admin = login(ADMIN_EMAIL);
        createUser(admin, "ACTIVO@demo.local", PASSWORD, "Repetido", "standard", AREA_A)
            .statusCode(409)
            .body("code", is("conflict"));
    }

    @Test
    void unRolSinAreaCorrespondeAResponde400() {
        Client admin = login(ADMIN_EMAIL);
        createUser(admin, "sinarea@demo.local", PASSWORD, "Sin Área", "standard", null)
            .statusCode(400);
    }

    // ------------------------------------------------------------------
    // Edición y estado
    // ------------------------------------------------------------------

    @Test
    void desactivarRevocaLasSesionesAbiertas() {
        Client admin = login(ADMIN_EMAIL);
        String id = createUser(admin, "temporal@demo.local", PASSWORD, "Temporal", "standard", AREA_A)
            .statusCode(201).extract().jsonPath().getString("id");

        Client temporal = login("temporal@demo.local");
        given().cookie("session", temporal.session())
            .when().get("/api/auth/sessions/current")
            .then().statusCode(200);

        patchUser(admin, id, Map.of("active", false))
            .statusCode(200)
            .body("active", is(false));

        given().cookie("session", temporal.session())
            .when().get("/api/auth/sessions/current")
            .then().statusCode(401);
    }

    @Test
    void unAdministradorNoPuedeDesactivarseNiCambiarSuRol() {
        Client admin = login(ADMIN_EMAIL);
        patchUser(admin, ADMIN, Map.of("active", false)).statusCode(403);
        patchUser(admin, ADMIN, Map.of("role", "manager")).statusCode(403);
    }

    @Test
    void cambiaElRolYElArea() {
        Client admin = login(ADMIN_EMAIL);
        String id = createUser(admin, "cambia@demo.local", PASSWORD, "Cambia", "standard", AREA_A)
            .statusCode(201).extract().jsonPath().getString("id");

        // A gerente: el área propia pasa a ser nula.
        patchUser(admin, id, Map.of("role", "manager"))
            .statusCode(200)
            .body("role", is("manager"))
            .body("areaId", is(nullValue()));

        // Vuelve a estándar en otra área.
        patchUser(admin, id, Map.of("role", "standard", "areaId", AREA_B))
            .statusCode(200)
            .body("role", is("standard"))
            .body("areaId", is(AREA_B));
    }

    @Test
    void editarUnUsuarioInexistenteResponde404() {
        Client admin = login(ADMIN_EMAIL);
        patchUser(admin, "00000000-0000-0000-0000-000000000000", Map.of("active", false))
            .statusCode(404);
    }

    // ------------------------------------------------------------------
    // Listado y búsqueda
    // ------------------------------------------------------------------

    @Test
    void listaYBuscausuarios() {
        Client admin = login(ADMIN_EMAIL);

        List<Map<String, Object>> all = given().cookie("session", admin.session())
            .when().get("/api/users").then().statusCode(200)
            .extract().as(new TypeRef<List<Map<String, Object>>>() {
            });
        assertThat(all.size(), is(6));

        List<Map<String, Object>> found = given().cookie("session", admin.session())
            .queryParam("search", "jefe")
            .when().get("/api/users").then().statusCode(200)
            .extract().as(new TypeRef<List<Map<String, Object>>>() {
            });
        assertThat(found.size(), is(1));
        assertThat(found.get(0).get("email"), is(HEAD_EMAIL));
    }

    @Test
    void filtraUsuariosPorArea() {
        Client admin = login(ADMIN_EMAIL);

        List<Map<String, Object>> areaA = listByArea(admin, AREA_A);
        assertThat(areaA.size(), is(3));
        assertThat(emails(areaA), hasItems(ACTIVE_EMAIL, INACTIVE_EMAIL, HEAD_EMAIL));

        List<Map<String, Object>> areaB = listByArea(admin, AREA_B);
        assertThat(areaB.size(), is(1));
        assertThat(areaB.get(0).get("email"), is(OTHER_EMAIL));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static ValidatableResponse createUser(
        Client admin, String email, String password, String name, String role, String areaId
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("name", name);
        body.put("role", role);
        if (areaId != null) {
            body.put("areaId", areaId);
        }
        return authenticated(admin)
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/users")
            .then();
    }

    private static ValidatableResponse patchUser(Client admin, String id, Map<String, Object> body) {
        return authenticated(admin)
            .contentType(ContentType.JSON)
            .body(body)
            .when().patch("/api/users/" + id)
            .then();
    }

    private static List<Map<String, Object>> listByArea(Client admin, String areaId) {
        return given().cookie("session", admin.session())
            .queryParam("areaId", areaId)
            .when().get("/api/users").then().statusCode(200)
            .extract().as(new TypeRef<List<Map<String, Object>>>() {
            });
    }

    private static List<String> emails(List<Map<String, Object>> users) {
        return users.stream().map(user -> (String) user.get("email")).toList();
    }
}
