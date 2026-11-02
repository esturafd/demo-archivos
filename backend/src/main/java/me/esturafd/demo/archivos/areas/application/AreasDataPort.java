package me.esturafd.demo.archivos.areas.application;

import me.esturafd.demo.archivos.areas.application.model.Area;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de datos del módulo `areas`.
 *
 * <p>El alcance de lectura lo aplican las políticas RLS (administrador todas,
 * usuario la suya, gerente las que gestiona). La escritura solo la permite RLS
 * al administrador; la guarda de rol de la ruta da el {@code 403} antes de
 * llegar aquí.
 */
public interface AreasDataPort {

    /** Áreas visibles para el actor, ordenadas por nombre. */
    List<Area> listVisibleAreas(UUID actorId, String requestId, String ipAddress);

    /** Área visible por su identificador, o vacío si no existe o queda fuera de alcance. */
    Optional<Area> findVisibleArea(UUID areaId, UUID actorId, String requestId, String ipAddress);

    /** Crea un área. Un nombre repetido sin distinguir mayúsculas provoca un conflicto (409). */
    Area createArea(
        UUID actorId,
        String name,
        String description,
        String requestId,
        String ipAddress
    );

    /**
     * Edita o desactiva un área. Los campos {@code null} se conservan. Devuelve
     * vacío si el área no existe o no es visible (la política de UPDATE afecta a
     * cero filas en silencio).
     */
    Optional<Area> updateArea(
        UUID actorId,
        UUID areaId,
        String name,
        String description,
        Boolean active,
        String requestId,
        String ipAddress
    );
}
