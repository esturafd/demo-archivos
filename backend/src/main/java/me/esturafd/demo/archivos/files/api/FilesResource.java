package me.esturafd.demo.archivos.files.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import me.esturafd.demo.archivos.files.api.dto.FilePageResponse;
import me.esturafd.demo.archivos.files.api.dto.FileView;
import me.esturafd.demo.archivos.files.application.DeleteFileUseCase;
import me.esturafd.demo.archivos.files.application.DownloadFileUseCase;
import me.esturafd.demo.archivos.files.application.FindFileUseCase;
import me.esturafd.demo.archivos.files.application.ListFilesUseCase;
import me.esturafd.demo.archivos.files.application.UploadFileUseCase;
import me.esturafd.demo.archivos.files.application.model.DownloadedFile;
import me.esturafd.demo.archivos.files.application.model.StoredFile;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirementsSet;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

/**
 * Recurso de archivos.
 *
 * <p>Todas las rutas exigen sesión; las que modifican estado pasan además por la
 * comprobación CSRF. Nunca se expone la clave interna del objeto ni una URL
 * directa: el contenido se transmite siempre por aquí.
 */
@Path("/api/files")
@Produces(MediaType.APPLICATION_JSON)
@RequiresSession
@Tag(name = "Archivos")
@APIResponses({
    @APIResponse(
        responseCode = "401",
        description = "No hay una sesión válida.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "500",
        description = "Error inesperado.",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class FilesResource {

    private static final String FILE_PART = "file";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";

    @Inject
    ListFilesUseCase listFiles;

    @Inject
    UploadFileUseCase uploadFile;

    @Inject
    FindFileUseCase findFile;

    @Inject
    DownloadFileUseCase downloadFile;

    @Inject
    DeleteFileUseCase deleteFile;

    @Inject
    CurrentSession currentSession;

    @Inject
    RequestContext context;

    /** Lista y busca archivos visibles para el usuario, con paginación por cursor. */
    @GET
    @Operation(
        summary = "Listar archivos",
        description = """
            Archivos visibles para el usuario, con búsqueda por nombre y paginación
            por cursor. El alcance lo acota RLS: cada rol ve lo que le corresponde.
            """
    )
    @APIResponse(
        responseCode = "200",
        description = "Página de archivos.",
        content = @Content(schema = @Schema(implementation = FilePageResponse.class)))
    public FilePageResponse list(
        @Parameter(description = "Texto a buscar en el nombre del archivo.")
        @QueryParam("search") String search,
        @Parameter(description = "Cursor opaco de la página anterior (`nextCursor`).")
        @QueryParam("cursor") String cursor,
        @Parameter(description = "Tamaño de página. Por defecto 20, máximo 100.")
        @QueryParam("limit") Integer limit
    ) {
        SessionUser actor = currentSession.require();
        return FilePageResponse.from(
            listFiles.list(actor, search, cursor, limit, context.requestId(), context.clientIp()));
    }

    /** Carga un archivo. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Cargar un archivo",
        description = """
            Recibe el archivo como `multipart/form-data` en la parte `file`. El tipo
            se valida por la firma real del contenido, no por lo que declare el
            cliente. Máximo 20 MB.
            """
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @RequestBody(
        required = true,
        description = "El archivo, en la parte `file`.",
        content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA,
            schema = @Schema(
                type = SchemaType.OBJECT,
                properties = @SchemaProperty(
                    name = FILE_PART,
                    type = SchemaType.STRING,
                    format = "binary",
                    description = "Contenido del archivo."))))
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Archivo cargado.",
            headers = @Header(
                name = "Location",
                description = "Ruta del archivo creado, `/api/files/{id}`."),
            content = @Content(schema = @Schema(implementation = FileView.class))),
        @APIResponse(
            responseCode = "400",
            description = "Falta la parte `file` o no se pudo leer.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "413",
            description = "El archivo supera los 20 MB.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "415",
            description = "El tipo de archivo no está permitido.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "422",
            description = "El contenido no coincide con el tipo declarado.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Response upload(MultipartFormDataInput input) {
        SessionUser actor = currentSession.require();
        FormValue part = filePart(input);

        StoredFile stored = uploadFile.upload(
            actor,
            part.getFileName(),
            declaredType(part),
            readContent(part),
            context.requestId(),
            context.clientIp()
        );

        return Response.status(Response.Status.CREATED)
            .header(HttpHeaders.LOCATION, "/api/files/" + stored.id())
            .entity(FileView.from(stored))
            .build();
    }

    /** Metadatos visibles de un archivo. */
    @GET
    @Path("/{id}")
    @Operation(
        summary = "Consultar un archivo",
        description = "Metadatos visibles de un archivo. Nunca incluye la clave interna."
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Metadatos del archivo.",
            content = @Content(schema = @Schema(implementation = FileView.class))),
        @APIResponse(
            responseCode = "404",
            description = "El archivo no existe o queda fuera del alcance.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FileView find(
        @Parameter(description = "Identificador del archivo.", required = true) @PathParam("id") UUID id
    ) {
        SessionUser actor = currentSession.require();
        return FileView.from(findFile.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Descarga el contenido del archivo. */
    @GET
    @Path("/{id}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        summary = "Descargar un archivo",
        description = """
            Transmite el contenido del archivo con su tipo real y un
            `Content-Disposition` de tipo `attachment`.
            """
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Contenido del archivo.",
            content = @Content(
                mediaType = MediaType.APPLICATION_OCTET_STREAM,
                schema = @Schema(type = SchemaType.STRING, format = "binary"))),
        @APIResponse(
            responseCode = "404",
            description = "El archivo no existe o queda fuera del alcance.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Response content(
        @Parameter(description = "Identificador del archivo.", required = true) @PathParam("id") UUID id
    ) {
        SessionUser actor = currentSession.require();
        DownloadedFile file = downloadFile.download(id, actor, context.requestId(), context.clientIp());

        StreamingOutput body = output -> {
            try (InputStream content = file.content()) {
                content.transferTo(output);
            }
        };

        return Response.ok(body)
            .type(file.mimeType())
            .header(HttpHeaders.CONTENT_LENGTH, file.sizeBytes())
            .header(CONTENT_DISPOSITION, disposition(file.originalName()))
            .build();
    }

    /** Elimina lógicamente el archivo. */
    @DELETE
    @Path("/{id}")
    @Operation(
        summary = "Eliminar un archivo",
        description = "Marca el archivo como eliminado; no borra el objeto de inmediato."
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Archivo eliminado."),
        @APIResponse(
            responseCode = "404",
            description = "El archivo no existe o queda fuera del alcance.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Response delete(
        @Parameter(description = "Identificador del archivo.", required = true) @PathParam("id") UUID id
    ) {
        SessionUser actor = currentSession.require();
        deleteFile.delete(id, actor, context.requestId(), context.clientIp());
        return Response.noContent().build();
    }

    // ------------------------------------------------------------------
    // Apoyo del multipart
    // ------------------------------------------------------------------

    private static FormValue filePart(MultipartFormDataInput input) {
        Collection<FormValue> values = input.getValues().get(FILE_PART);
        if (values == null || values.isEmpty()) {
            throw ApiException.badRequest("Falta la parte 'file'.");
        }
        FormValue part = values.iterator().next();
        if (!part.isFileItem()) {
            throw ApiException.badRequest("La parte 'file' no es un archivo.");
        }
        return part;
    }

    private static String declaredType(FormValue part) {
        if (part.getHeaders() == null) {
            return null;
        }
        return part.getHeaders().entrySet().stream()
            .filter(entry -> entry.getKey() != null
                && entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))
            .map(entry -> entry.getValue().isEmpty() ? null : entry.getValue().get(0))
            .findFirst()
            .orElse(null);
    }

    private static byte[] readContent(FormValue part) {
        try (InputStream content = part.getFileItem().getInputStream()) {
            return content.readAllBytes();
        } catch (IOException failure) {
            throw ApiException.badRequest("No se pudo leer el archivo.");
        }
    }

    private static String disposition(String originalName) {
        String safe = originalName == null || originalName.isBlank()
            ? "archivo"
            : originalName.replaceAll("[\\r\\n\"\\\\]", "_");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }
}
