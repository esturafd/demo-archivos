package me.esturafd.demo.archivos.users.api;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.esturafd.demo.archivos.shared.error.api.ApiError;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import me.esturafd.demo.archivos.shared.security.api.CurrentSession;
import me.esturafd.demo.archivos.shared.security.api.RequiresRole;
import me.esturafd.demo.archivos.shared.security.application.model.AppRole;
import me.esturafd.demo.archivos.shared.security.api.RequiresSession;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.users.api.dto.CreateUserRequest;
import me.esturafd.demo.archivos.users.api.dto.UpdateUserRequest;
import me.esturafd.demo.archivos.users.api.dto.UserDetailView;
import me.esturafd.demo.archivos.users.application.CreateUserUseCase;
import me.esturafd.demo.archivos.users.application.FindUserUseCase;
import me.esturafd.demo.archivos.users.application.ListUsersUseCase;
import me.esturafd.demo.archivos.users.application.UpdateUserUseCase;
import me.esturafd.demo.archivos.users.application.model.UserDetail;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirementsSet;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * Recurso de administración de usuarios.
 *
 * <p>Todas las rutas son del administrador. La base vuelve a comprobarlo con sus
 * políticas y con el trigger que protege al propio administrador. Ninguna
 * respuesta incluye {@code password_hash}.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiresSession
@RequiresRole(AppRole.ADMIN)
@Tag(name = "Usuarios")
@APIResponses({
    @APIResponse(
        responseCode = "401",
        description = "No hay una sesión válida.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "403",
        description = "La sesión no tiene nivel de administrador.",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @APIResponse(
        responseCode = "500",
        description = "Error inesperado.",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class UsersResource {

    @Inject
    ListUsersUseCase listUsers;

    @Inject
    FindUserUseCase findUser;

    @Inject
    CreateUserUseCase createUser;

    @Inject
    UpdateUserUseCase updateUser;

    @Inject
    CurrentSession currentSession;

    @Inject
    RequestContext context;

    /** Lista y busca usuarios. */
    @GET
    @Operation(
        summary = "Listar usuarios",
        description = "Usuarios de la plataforma, con búsqueda opcional por correo o nombre."
    )
    @APIResponse(
        responseCode = "200",
        description = "Usuarios encontrados.")
    public List<UserDetailView> list(
        @Parameter(description = "Texto a buscar en correo o nombre.")
        @QueryParam("search") String search
    ) {
        SessionUser actor = currentSession.require();
        return listUsers.list(actor, search, context.requestId(), context.clientIp())
            .stream()
            .map(UserDetailView::from)
            .toList();
    }

    /** Crea un usuario. La contraseña se hashea en la API; solo el hash llega a la base. */
    @POST
    @Operation(
        summary = "Crear un usuario",
        description = """
            Da de alta un usuario. La contraseña se hashea en la API: solo el hash
            llega a la base. Un correo repetido responde con conflicto.
            """
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(
            responseCode = "201",
            description = "Usuario creado.",
            headers = @Header(
                name = "Location",
                description = "Ruta del usuario creado, `/api/users/{id}`."),
            content = @Content(schema = @Schema(implementation = UserDetailView.class))),
        @APIResponse(
            responseCode = "400",
            description = "El cuerpo no supera la validación.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "409",
            description = "Ya existe un usuario con ese correo.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public Response create(@Valid CreateUserRequest request) {
        SessionUser actor = currentSession.require();
        UserDetail user = createUser.create(
            actor, request.email(), request.password(), request.name(), request.role(), request.areaId(),
            context.requestId(), context.clientIp());

        return Response.status(Response.Status.CREATED)
            .header(HttpHeaders.LOCATION, "/api/users/" + user.id())
            .entity(UserDetailView.from(user))
            .build();
    }

    /** Consulta un usuario. */
    @GET
    @Path("/{id}")
    @Operation(summary = "Consultar un usuario", description = "Datos de un usuario por su identificador.")
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Datos del usuario.",
            content = @Content(schema = @Schema(implementation = UserDetailView.class))),
        @APIResponse(
            responseCode = "404",
            description = "El usuario no existe.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserDetailView find(
        @Parameter(description = "Identificador del usuario.", required = true) @PathParam("id") UUID id
    ) {
        SessionUser actor = currentSession.require();
        return UserDetailView.from(findUser.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Edita nombre, rol, área o estado. La desactivación revoca las sesiones abiertas. */
    @PATCH
    @Path("/{id}")
    @Operation(
        summary = "Editar un usuario",
        description = """
            Cambia nombre, rol, área o estado. Los campos ausentes se conservan.
            Desactivar a un usuario revoca sus sesiones abiertas.
            """
    )
    @SecurityRequirementsSet({
        @SecurityRequirement(name = "sessionCookie"),
        @SecurityRequirement(name = "csrfHeader")
    })
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Usuario actualizado.",
            content = @Content(schema = @Schema(implementation = UserDetailView.class))),
        @APIResponse(
            responseCode = "400",
            description = "El cuerpo no supera la validación.",
            content = @Content(schema = @Schema(implementation = ApiError.class))),
        @APIResponse(
            responseCode = "404",
            description = "El usuario no existe.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserDetailView update(
        @Parameter(description = "Identificador del usuario.", required = true) @PathParam("id") UUID id,
        @Valid UpdateUserRequest request
    ) {
        SessionUser actor = currentSession.require();
        UserDetail user = updateUser.update(
            id, actor, request.name(), request.role(), request.areaId(), request.active(),
            context.requestId(), context.clientIp());
        return UserDetailView.from(user);
    }
}
