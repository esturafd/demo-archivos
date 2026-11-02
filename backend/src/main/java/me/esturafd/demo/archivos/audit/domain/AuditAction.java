package me.esturafd.demo.archivos.audit.domain;

import java.util.Optional;

/**
 * Acciones registradas en el historial.
 *
 * <p>Coinciden con el tipo {@code audit.audit_action} de la base.
 */
public enum AuditAction {

    LOGIN_SUCCEEDED("login_succeeded"),
    LOGIN_FAILED("login_failed"),
    LOGOUT("logout"),
    FILE_UPLOADED("file_uploaded"),
    FILE_DOWNLOADED("file_downloaded"),
    FILE_DELETED("file_deleted"),
    ACCESS_DENIED("access_denied");

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Acción a partir de su valor, o vacío si no se reconoce. */
    public static Optional<AuditAction> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        for (AuditAction action : values()) {
            if (action.value.equalsIgnoreCase(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
