package me.esturafd.demo.archivos.users.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.util.List;

/**
 * Caso de uso del listado y la búsqueda de usuarios.
 */
@ApplicationScoped
public class ListUsersUseCase {

    @Inject
    UsersDataPort data;

    public List<UserDetail> list(SessionUser actor, String search, String requestId, String ipAddress) {
        String text = search == null || search.isBlank() ? null : search.trim();
        return data.listVisibleUsers(text, actor.id(), requestId, ipAddress);
    }
}
