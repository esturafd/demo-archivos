package me.esturafd.demo.archivos.files;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.infrastructure.TestFilesStoragePort;
import me.esturafd.demo.archivos.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Pruebas de la API de archivos con cada rol.
 *
 * <p>La conexión de la aplicación cambia al rol de ejecución dentro de cada
 * transacción, así que el alcance lo aplica RLS de verdad: estas pruebas
 * comprueban el aislamiento entre áreas y los intentos fuera de alcance.
 */
@QuarkusTest
class FilesResourceTest extends ApiTestBase {

    private static final String TEXT_TYPE = "text/plain";
    private static final byte[] CONTENT = "contenido de prueba".getBytes(StandardCharsets.UTF_8);

    @Inject
    TestFilesStoragePort storage;

    @BeforeEach
    void limpiarAlmacenamiento() {
        storage.clear();
    }

    // ------------------------------------------------------------------
    // Alcance por rol
    // ------------------------------------------------------------------

    @Test
    void elEstandarVeSoloLosSuyos() {
        Client active = login(ACTIVE_EMAIL);
        Client other = login(OTHER_EMAIL);
        upload(active, "propio.txt");
        upload(other, "ajeno.txt");

        assertThat(names(list(active)), is(List.of("propio.txt")));
        assertThat(names(list(other)), is(List.of("ajeno.txt")));
    }

    @Test
    void elJefeVeLosEstandarDeSuArea() {
        Client head = login(HEAD_EMAIL);
        upload(head, "jefe.txt");
        upload(login(ACTIVE_EMAIL), "estandar-a.txt");
        upload(login(OTHER_EMAIL), "estandar-b.txt");

        List<String> names = names(list(head));
        assertThat(names.size(), is(2));
        assertThat(names, not(hasItem("estandar-b.txt")));
    }

    @Test
    void elGerenteVeLasAreasQueGestiona() {
        Client manager = login(MANAGER_EMAIL);
        upload(manager, "gerente.txt");
        upload(login(ACTIVE_EMAIL), "estandar-a.txt");
        upload(login(HEAD_EMAIL), "jefe.txt");
        upload(login(OTHER_EMAIL), "estandar-b.txt");

        List<String> names = names(list(manager));
        assertThat(names.size(), is(3));
        assertThat(names, not(hasItem("estandar-b.txt")));
    }

    @Test
    void elAdministradorVeTodos() {
        Client admin = login(ADMIN_EMAIL);
        upload(login(ACTIVE_EMAIL), "a.txt");
        upload(login(OTHER_EMAIL), "b.txt");

        assertThat(list(admin).size(), is(2));
    }

    @Test
    void filtraPorPropietarioDentroDelAlcance() {
        Client manager = login(MANAGER_EMAIL);
        upload(login(ACTIVE_EMAIL), "de-activo.txt");
        upload(login(OTHER_EMAIL), "de-otro.txt");
        upload(manager, "del-gerente.txt");

        // El propietario de su área.
        assertThat(names(listByOwner(manager, ACTIVE)), is(List.of("de-activo.txt")));
        // Fuera de su alcance: el filtro no lo salta (RLS sigue acotando).
        assertThat(listByOwner(manager, OTHER), is(List.of()));
        // Él mismo.
        assertThat(names(listByOwner(manager, MANAGER)), is(List.of("del-gerente.txt")));
    }

    @Test
    void elEstandarNoVeArchivosAjenosAunqueFiltrePorPropietario() {
        Client active = login(ACTIVE_EMAIL);
        upload(active, "propio.txt");
        upload(login(OTHER_EMAIL), "de-otro.txt");

        assertThat(names(listByOwner(active, ACTIVE)), is(List.of("propio.txt")));
        assertThat(listByOwner(active, OTHER), is(List.of()));
    }

    // ------------------------------------------------------------------
    // Propietarios visibles (selector de usuario)
    // ------------------------------------------------------------------

    @Test
    void elEstandarSoloSeVeASiMismoComoPropietario() {
        Client active = login(ACTIVE_EMAIL);
        upload(active, "propio.txt");

        assertThat(ownerIds(active), is(List.of(ACTIVE)));
    }

    @Test
    void elGerenteVeLosPropietariosDeSuAreaYASiMismo() {
        Client manager = login(MANAGER_EMAIL);
        upload(login(ACTIVE_EMAIL), "de-activo.txt");
        upload(login(OTHER_EMAIL), "de-otro.txt");

        List<String> owners = ownerIds(manager);
        assertThat(owners, hasItem(ACTIVE));
        assertThat(owners, hasItem(MANAGER));      // él mismo, aunque no tenga archivos
        assertThat(owners, not(hasItem(OTHER)));   // fuera de su alcance
    }

    @Test
    void elAdministradorVeTodosLosPropietarios() {
        Client admin = login(ADMIN_EMAIL);
        upload(login(ACTIVE_EMAIL), "a.txt");
        upload(login(OTHER_EMAIL), "b.txt");

        List<String> owners = ownerIds(admin);
        assertThat(owners, hasItem(ACTIVE));
        assertThat(owners, hasItem(OTHER));
        assertThat(owners, hasItem(ADMIN));        // él mismo
    }

    // ------------------------------------------------------------------
    // Descarga y eliminación
    // ------------------------------------------------------------------

    @Test
    void descargaElContenido() {
        Client active = login(ACTIVE_EMAIL);
        String id = upload(active, "descarga.txt");

        byte[] downloaded = given().cookie("session", active.session())
            .when().get("/api/files/" + id + "/content")
            .then().statusCode(200)
            .extract().asByteArray();

        assertThat(downloaded, is(CONTENT));
    }

    @Test
    void elArchivoEliminadoDejaDeEstarDisponible() {
        Client active = login(ACTIVE_EMAIL);
        String id = upload(active, "borrar.txt");

        authenticated(active)
            .when().delete("/api/files/" + id)
            .then().statusCode(204);

        given().cookie("session", active.session())
            .when().get("/api/files/" + id)
            .then().statusCode(404);
        given().cookie("session", active.session())
            .when().get("/api/files/" + id + "/content")
            .then().statusCode(404);
        assertThat(list(active).size(), is(0));
    }

    @Test
    void fueraDeAlcanceResponde404YRegistraAccesoDenegado() throws Exception {
        Client active = login(ACTIVE_EMAIL);
        String id = upload(active, "privado.txt");
        Client other = login(OTHER_EMAIL);

        given().cookie("session", other.session())
            .when().get("/api/files/" + id + "/content")
            .then().statusCode(404);

        assertThat(hayAccesoDenegado(id), is(true));
    }

    // ------------------------------------------------------------------
    // Validación del contenido
    // ------------------------------------------------------------------

    @Test
    void rechazaUnArchivoMayorDeVeinteMegas() {
        Client active = login(ACTIVE_EMAIL);
        postFile(active, "grande.txt", TEXT_TYPE, new byte[20_971_521])
            .statusCode(413)
            .body("code", is("payload_too_large"));
    }

    @Test
    void rechazaContenidoQueNoCoincideConElTipoDeclarado() {
        Client active = login(ACTIVE_EMAIL);
        postFile(active, "falso.png", "image/png", CONTENT)
            .statusCode(422)
            .body("code", is("unprocessable_content"));
    }

    @Test
    void rechazaUnTipoNoPermitido() {
        Client active = login(ACTIVE_EMAIL);
        postFile(active, "binario.dat", "application/x-msdownload", CONTENT)
            .statusCode(415)
            .body("code", is("unsupported_media_type"));
    }

    @Test
    void ningunaRespuestaIncluyeLaClaveInterna() {
        Client active = login(ACTIVE_EMAIL);
        String id = upload(active, "sin-clave.txt");

        String listBody = given().cookie("session", active.session())
            .when().get("/api/files").then().statusCode(200)
            .extract().body().asString();
        String metaBody = given().cookie("session", active.session())
            .when().get("/api/files/" + id).then().statusCode(200)
            .extract().body().asString();

        for (String body : List.of(listBody, metaBody)) {
            assertThat(body, not(containsString("storage_key")));
            assertThat(body, not(containsString("storageKey")));
            assertThat(body, not(containsString("objects/")));
        }
    }

    @Test
    void laCompensacionBorraElObjetoSiFallaElRegistro() throws Exception {
        Client active = login(ACTIVE_EMAIL);
        desactivarArea(AREA_A);

        // La política de inserción rechaza la carga en un área inactiva; el
        // objeto ya se había subido, así que la compensación debe borrarlo.
        postFile(active, "falla.txt", TEXT_TYPE, CONTENT).statusCode(403);

        assertThat(storage.size(), is(0));
        assertThat(list(active).size(), is(0));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static ValidatableResponse postFile(
        Client client, String name, String type, byte[] content
    ) {
        return authenticated(client)
            .multiPart("file", name, content, type)
            .when().post("/api/files")
            .then();
    }

    private static String upload(Client client, String name) {
        return postFile(client, name, TEXT_TYPE, CONTENT)
            .statusCode(201)
            .extract().jsonPath().getString("id");
    }

    private static List<Map<String, Object>> list(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/files")
            .then().statusCode(200)
            .extract().jsonPath().getList("items");
    }

    private static List<Map<String, Object>> listByOwner(Client client, String ownerId) {
        return given().cookie("session", client.session())
            .queryParam("ownerId", ownerId)
            .when().get("/api/files")
            .then().statusCode(200)
            .extract().jsonPath().getList("items");
    }

    private static List<String> ownerIds(Client client) {
        return given().cookie("session", client.session())
            .when().get("/api/files/owners")
            .then().statusCode(200)
            .extract().jsonPath().getList("id");
    }

    private static List<String> names(List<Map<String, Object>> files) {
        return files.stream().map(file -> (String) file.get("originalName")).toList();
    }

    private static void desactivarArea(String areaId) throws Exception {
        try (Connection connection = conectar();
             Statement statement = connection.createStatement()) {
            statement.execute(
                "UPDATE app.areas SET is_active = false, updated_at = now() "
                    + "WHERE id = '" + areaId + "'");
        }
    }

    private static boolean hayAccesoDenegado(String fileId) throws Exception {
        try (Connection connection = conectar();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT count(*) FROM audit.action_logs "
                     + "WHERE action = 'access_denied' AND file_id = '" + fileId + "'")) {
            rows.next();
            return rows.getInt(1) > 0;
        }
    }
}
