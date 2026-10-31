package me.esturafd.demo.archivos.auth.api.dto;

import me.esturafd.demo.archivos.shared.data.application.model.SessionUser;

/**
 * Vista pública de un usuario. Nunca incluye hash de contraseña ni de token.
 */
public record UserView(
    String id,
    String email,
    String name,
    String role,
    String areaId,
    String areaName,
    Boolean areaActive,
    boolean active
) {

    public static UserView from(SessionUser user) {
        return new UserView(
            user.id().toString(),
            user.email(),
            user.name(),
            user.role(),
            user.areaId() == null ? null : user.areaId().toString(),
            user.areaName(),
            user.areaActive(),
            user.active()
        );
    }
}
