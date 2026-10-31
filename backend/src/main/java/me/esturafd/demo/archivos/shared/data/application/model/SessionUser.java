package me.esturafd.demo.archivos.shared.data.application.model;

import java.util.UUID;

/**
 * Usuario resuelto en la petición en curso, con los datos que necesita la
 * interfaz y la autorización.
 *
 * <p>El alcance (rol y área) se lee en cada petición, de modo que un cambio de
 * rol o de área aplique en la siguiente acción sin esperar a un nuevo inicio de
 * sesión.
 *
 * @param id          identificador del usuario
 * @param email       correo
 * @param name        nombre para mostrar
 * @param role        rol de negocio
 * @param areaId      área propia, o {@code null}
 * @param active      si la cuenta sigue activa
 * @param areaName    nombre del área, o {@code null}
 * @param areaActive  si el área admite nuevas operaciones, o {@code null}
 */
public record SessionUser(
    UUID id,
    String email,
    String name,
    String role,
    UUID areaId,
    boolean active,
    String areaName,
    Boolean areaActive
) {
}
