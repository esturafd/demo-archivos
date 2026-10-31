package me.esturafd.demo.archivos.shared.security.application.model;

import java.util.Optional;

/**
 * Roles de negocio con su nivel jerárquico.
 *
 * <p>Los valores coinciden con el tipo {@code app.app_role} de la base. El nivel
 * permite expresar guardas como «manager o superior» sin enumerar roles, de modo
 * que añadir un rol nuevo (por ejemplo entre manager y admin) solo exija darle un
 * nivel.
 */
public enum AppRole {

    STANDARD(0),
    AREA_HEAD(1),
    MANAGER(2),
    ADMIN(3);

    private final int level;

    AppRole(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** True si este rol está al mismo nivel o por encima del indicado. */
    public boolean atLeast(AppRole required) {
        return level >= required.level;
    }

    /** Rol a partir de su valor, o vacío si no se reconoce. */
    public static Optional<AppRole> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        for (AppRole role : values()) {
            if (role.name().equalsIgnoreCase(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
