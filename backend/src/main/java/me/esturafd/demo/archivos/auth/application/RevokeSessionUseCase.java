package me.esturafd.demo.archivos.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.data.application.DataMediator;

import java.util.UUID;

/**
 * Caso de uso de revocación de una sesión concreta por su identificador.
 *
 * <p>La autorización la aplica la base de datos: solo el propietario o un
 * administrador pueden revocarla.
 */
@ApplicationScoped
public class RevokeSessionUseCase {

    @Inject
    DataMediator data;

    public boolean revoke(UUID actorUserId, UUID sessionId, String requestId, String ipAddress) {
        return data.revokeUserSession(actorUserId, sessionId, requestId, ipAddress);
    }
}
