package me.esturafd.demo.archivos.files.application.model;

import java.util.UUID;

/**
 * Propietario de archivos visible para el actor.
 *
 * <p>Sirve para el selector de usuario del frontend: los usuarios cuyos archivos
 * puede ver el actor, más él mismo.
 *
 * @param id    identificador del usuario
 * @param email correo del usuario
 * @param name  nombre del usuario, o {@code null} si no es visible
 */
public record FileOwner(UUID id, String email, String name) {
}
