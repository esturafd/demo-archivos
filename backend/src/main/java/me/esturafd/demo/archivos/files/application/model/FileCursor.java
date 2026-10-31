package me.esturafd.demo.archivos.files.application.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Posición de continuación del listado: {@code (uploaded_at, id)} del último
 * elemento entregado.
 *
 * <p>Se transporta como un valor opaco (base64) para que el cliente no dependa
 * de su forma. No es un secreto, solo una posición.
 */
public record FileCursor(Instant uploadedAt, UUID id) {

    public static String encode(Instant uploadedAt, UUID id) {
        String raw = uploadedAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<FileCursor> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return Optional.of(new FileCursor(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                UUID.fromString(parts[1])
            ));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
