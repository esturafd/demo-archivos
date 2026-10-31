package me.esturafd.demo.archivos.shared.data.application.model;

import java.util.UUID;

/**
 * Datos mínimos de un usuario para resolver un inicio de sesión.
 *
 * <p>Proviene de {@code app.login_lookup}, la única vía de acceso a las
 * credenciales antes de que exista contexto de usuario. Incluye el hash porque
 * la verificación la hace la API, no la base de datos.
 *
 * @param id           identificador del usuario
 * @param email        correo tal como está guardado
 * @param passwordHash hash bcrypt en formato MCF
 * @param active       si la cuenta está activa
 * @param role         rol de negocio ({@code standard}, {@code area_head}, {@code manager}, {@code admin})
 * @param areaId       área propia, o {@code null} para gerente y administrador
 */
public record LoginUser(
    UUID id,
    String email,
    String passwordHash,
    boolean active,
    String role,
    UUID areaId
) {
}
