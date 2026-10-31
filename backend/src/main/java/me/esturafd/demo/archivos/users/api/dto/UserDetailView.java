package me.esturafd.demo.archivos.users.api.dto;

import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.time.Instant;

/**
 * Vista pública de un usuario. Nunca incluye {@code password_hash}.
 */
public record UserDetailView(
    String id,
    String email,
    String name,
    String role,
    String areaId,
    String areaName,
    boolean active,
    Instant createdAt
) {

    public static UserDetailView from(UserDetail user) {
        return new UserDetailView(
            user.id().toString(),
            user.email(),
            user.name(),
            user.role(),
            user.areaId() == null ? null : user.areaId().toString(),
            user.areaName(),
            user.active(),
            user.createdAt()
        );
    }
}
