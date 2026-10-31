package me.esturafd.demo.archivos.managedareas.application;

import me.esturafd.demo.archivos.managedareas.application.model.ManagedArea;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de datos del módulo `managedareas`.
 *
 * <p>La lectura la acota RLS (el administrador ve las de cualquiera; un gerente,
 * solo las suyas). El reemplazo lo autoriza la ruta: solo el administrador.
 */
public interface ManagedAreasDataPort {

    /** Áreas que gestiona el gerente, ordenadas por nombre. */
    List<ManagedArea> listManagedAreas(
        UUID managerId,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /**
     * Reemplaza el conjunto completo en una sola transacción: borra las
     * asignaciones anteriores e inserta las nuevas. Devuelve el conjunto
     * resultante. Un área inexistente o inactiva provoca un error y revierte
     * todo.
     */
    List<ManagedArea> replaceManagedAreas(
        UUID managerId,
        UUID actorId,
        List<UUID> areaIds,
        String requestId,
        String ipAddress
    );
}
