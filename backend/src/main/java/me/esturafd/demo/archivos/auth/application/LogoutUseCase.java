package me.esturafd.demo.archivos.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.auth.application.AuthDataPort;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

/**
 * Caso de uso de cierre de sesión.
 *
 * <p>Revoca la sesión en el servidor y registra el evento. La cookie la invalida
 * la capa de API.
 */
@ApplicationScoped
public class LogoutUseCase {

    @Inject
    AuthDataPort data;

    public void logout(SessionUser user, String tokenHash, String requestId, String ipAddress) {
        data.closeSession(user.id(), tokenHash, user.email(), requestId, ipAddress);
    }
}
