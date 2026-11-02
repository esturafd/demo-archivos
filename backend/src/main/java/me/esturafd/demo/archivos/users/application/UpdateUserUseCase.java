package me.esturafd.demo.archivos.users.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.util.UUID;

/**
 * Caso de uso de la edición de un usuario.
 *
 * <p>Carga el estado actual y calcula el estado final: el área se deriva del rol
 * (propia para estándar y jefe de área, ninguna para gerente y administrador).
 * La desactivación revoca las sesiones en la misma transacción del adaptador.
 */
@ApplicationScoped
public class UpdateUserUseCase {

    @Inject
    UsersDataPort data;

    public UserDetail update(
        UUID userId,
        SessionUser actor,
        String name,
        String role,
        UUID areaId,
        Boolean active,
        String requestId,
        String ipAddress
    ) {
        UserDetail current = data.findVisibleUser(userId, actor.id(), requestId, ipAddress)
            .orElseThrow(ApiException::notFound);

        String finalName = name != null ? name : current.name();
        String finalRole = role != null ? role : current.role();
        boolean finalActive = active != null ? active : current.active();
        UUID finalArea = areaFor(finalRole, areaId != null ? areaId : current.areaId());

        return data.updateUser(
                actor.id(), userId, finalName, finalRole, finalArea, finalActive, requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }

    /** El área propia solo existe para estándar y jefe de área. */
    private static UUID areaFor(String role, UUID areaId) {
        return switch (role) {
            case "standard", "area_head" -> areaId;
            default -> null;
        };
    }
}
