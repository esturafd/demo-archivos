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
    public List<UserDetailView> list(@QueryParam("search") String search) {
        SessionUser actor = currentSession.require();
        return listUsers.list(actor, search, context.requestId(), context.clientIp())
            .stream()
            .map(UserDetailView::from)
            .toList();
    }

    /** Crea un usuario. La contraseña se hashea en la API; solo el hash llega a la base. */
    @POST
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
    public UserDetailView find(@PathParam("id") UUID id) {
        SessionUser actor = currentSession.require();
        return UserDetailView.from(findUser.find(id, actor, context.requestId(), context.clientIp()));
    }

    /** Edita nombre, rol, área o estado. La desactivación revoca las sesiones abiertas. */
    @PATCH
    @Path("/{id}")
    public UserDetailView update(@PathParam("id") UUID id, @Valid UpdateUserRequest request) {
        SessionUser actor = currentSession.require();
        UserDetail user = updateUser.update(
            id, actor, request.name(), request.role(), request.areaId(), request.active(),
            context.requestId(), context.clientIp());
        return UserDetailView.from(user);
    }
}
