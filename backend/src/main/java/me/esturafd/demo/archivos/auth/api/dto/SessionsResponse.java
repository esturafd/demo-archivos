package me.esturafd.demo.archivos.auth.api.dto;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Listado de sesiones activas de un usuario.
 */
@Schema(description = "Sesiones activas del usuario.")
public record SessionsResponse(
    @Schema(description = "Sesiones vigentes, con la actual marcada.")
    List<SessionView> sessions
) {
}
