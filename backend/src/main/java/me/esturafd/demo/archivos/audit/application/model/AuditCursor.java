package me.esturafd.demo.archivos.audit.application.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Posición de continuación del historial: {@code (occurred_at, id)} del último
 * evento entregado.
 *
 * <p>El identificador del historial es un {@code bigint}, no un UUID.
 */
public record AuditCursor(Instant occurredAt, long id) {

    public static String encode(Instant occurredAt, long id) {
        String raw = occurredAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<AuditCursor> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return Optional.of(new AuditCursor(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                Long.parseLong(parts[1])
            ));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
