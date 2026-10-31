package me.esturafd.demo.archivos.auth.api.dto;

/**
 * Respuesta con el usuario y la sesión actuales.
 *
 * @param user    usuario autenticado
 * @param session sesión en curso; puede faltar si aún no se ha resuelto
 */
public record CurrentSessionResponse(UserView user, SessionView session) {
}
