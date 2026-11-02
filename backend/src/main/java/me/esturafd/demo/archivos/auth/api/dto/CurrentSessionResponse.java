package me.esturafd.demo.archivos.auth.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Respuesta con el usuario y la sesión actuales.
 *
 * @param user    usuario autenticado
 * @param session sesión en curso; puede faltar si aún no se ha resuelto
 */
@Schema(description = "Usuario autenticado y datos de su sesión actual.")
public record CurrentSessionResponse(
    @Schema(description = "Usuario autenticado.")
    UserView user,
    @Schema(description = "Sesión en curso; puede faltar si aún no se ha resuelto.")
    SessionView session
) {
}
