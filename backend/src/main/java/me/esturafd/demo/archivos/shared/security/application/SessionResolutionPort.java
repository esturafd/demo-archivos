package me.esturafd.demo.archivos.shared.security.application;

import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de resolución de la sesión en curso.
 *
 * <p>Es transversal: lo usa el filtro que lee la cookie y lo necesitan todos los
 * módulos protegidos. Por eso vive en `shared` y no en `auth`. Solo resuelve la
 * sesión; su ciclo de vida (crear, cerrar, listar) pertenece a `auth`.
 */
public interface SessionResolutionPort {

    /** Usuario de una sesión válida, o vacío si no existe, expiró o está revocada. */
    Optional<UUID> validateToken(String tokenHash);

    /** Identificador de la sesión activa correspondiente a un token. */
    Optional<UUID> findSessionId(String tokenHash);

    /** Datos del usuario de la sesión, con el alcance vigente. */
    Optional<SessionUser> findUser(UUID userId, String requestId, String ipAddress);
}
