package me.esturafd.demo.archivos.users.application;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.util.UUID;

/**
 * Caso de uso del alta de un usuario.
 *
 * <p>La contraseña llega en claro por HTTPS y aquí se calcula su hash bcrypt; a
 * la base solo va el hash. El correo duplicado lo rechaza el índice único y se
 * traduce a {@code 409}; la coherencia entre rol y área la aplica el
 * {@code CHECK} y se traduce a {@code 400}.
 */
@ApplicationScoped
public class CreateUserUseCase {

    @Inject
    UsersDataPort data;

    public UserDetail create(
        SessionUser actor,
        String email,
        String rawPassword,
        String name,
        String role,
        UUID areaId,
        String requestId,
        String ipAddress
    ) {
        String passwordHash = BcryptUtil.bcryptHash(rawPassword);
        return data.createUser(
            actor.id(), email, passwordHash, name, role, areaId, requestId, ipAddress);
    }
}
