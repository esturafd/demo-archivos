package me.esturafd.demo.archivos.auth.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Resumen de una sesión activa de un usuario, para el listado de dispositivos
 * conectados.
 *
 * @param id          identificador de la sesión
 * @param createdAt   cuándo se creó
 * @param expiresAt   cuándo expira
 * @param lastSeenAt  última actividad, o {@code null}
 * @param ipAddress   IP de origen
 * @param userAgent   agente de usuario, o {@code null}
 */
public record SessionSummary(
    UUID id,
    Instant createdAt,
    Instant expiresAt,
    Instant lastSeenAt,
    String ipAddress,
    String userAgent
) {
}
