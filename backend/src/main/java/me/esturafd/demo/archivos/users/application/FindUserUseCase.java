package me.esturafd.demo.archivos.users.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.util.UUID;

/**
 * Caso de uso de la consulta de un usuario.
 */
@ApplicationScoped
public class FindUserUseCase {

    @Inject
    UsersDataPort data;

    public UserDetail find(UUID userId, SessionUser actor, String requestId, String ipAddress) {
        return data.findVisibleUser(userId, actor.id(), requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }
}
