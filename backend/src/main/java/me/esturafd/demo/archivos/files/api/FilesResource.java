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
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
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
    public FilePageResponse list(
        @QueryParam("search") String search,
        @QueryParam("cursor") String cursor,
        @QueryParam("limit") Integer limit
    ) {
        SessionUser actor = currentSession.require();
        return FilePageResponse.from(
            listFiles.list(actor, search, cursor, limit, context.requestId(), context.clientIp()));
    }

    /** Carga un archivo. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
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
    public FileView find(@PathParam("id") UUID id) {
        SessionUser actor = currentSession.require();
        return FileView.from(findFile.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Descarga el contenido del archivo. */
    @GET
    @Path("/{id}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response content(@PathParam("id") UUID id) {
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
    public Response delete(@PathParam("id") UUID id) {
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
