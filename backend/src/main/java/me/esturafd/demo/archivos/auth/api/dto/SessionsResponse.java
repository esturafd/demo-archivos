package me.esturafd.demo.archivos.auth.api.dto;

import java.util.List;

/**
 * Listado de sesiones activas de un usuario.
 */
public record SessionsResponse(List<SessionView> sessions) {
}
