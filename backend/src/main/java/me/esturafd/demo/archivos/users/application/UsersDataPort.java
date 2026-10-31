package me.esturafd.demo.archivos.users.application;

import me.esturafd.demo.archivos.users.application.model.UserDetail;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de datos del módulo `users`.
 *
 * <p>Todas las operaciones las hace un administrador, así que RLS le permite ver
 * y editar cualquier cuenta. Las reglas de rol y área, la unicidad del correo y
 * la protección del propio administrador las aplica la base de datos.
 */
public interface UsersDataPort {

    /** Usuarios que coinciden con la búsqueda, ordenados por correo. */
    List<UserDetail> listVisibleUsers(
        String search,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /** Usuario por su identificador, o vacío si no existe o no es visible. */
    Optional<UserDetail> findVisibleUser(
        UUID userId,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /** Crea un usuario con el hash ya calculado. Un correo duplicado provoca conflicto (409). */
    UserDetail createUser(
        UUID actorId,
        String email,
        String passwordHash,
        String name,
        String role,
        UUID areaId,
        String requestId,
        String ipAddress
    );

    /**
     * Actualiza nombre, rol, área y estado con los valores finales ya
     * calculados. Si el usuario queda inactivo, revoca sus sesiones en la misma
     * transacción. Devuelve vacío si el usuario no existe.
     */
    Optional<UserDetail> updateUser(
        UUID actorId,
        UUID userId,
        String name,
        String role,
        UUID areaId,
        boolean active,
        String requestId,
        String ipAddress
    );
}
