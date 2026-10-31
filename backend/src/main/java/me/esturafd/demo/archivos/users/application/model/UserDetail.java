package me.esturafd.demo.archivos.users.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Usuario con los datos que ve la administración.
 *
 * <p>No incluye {@code password_hash}: el hash nunca sale del módulo.
 */
public record UserDetail(
    UUID id,
    String email,
    String name,
    String role,
    UUID areaId,
    String areaName,
    boolean active,
    Instant createdAt
) {
}
