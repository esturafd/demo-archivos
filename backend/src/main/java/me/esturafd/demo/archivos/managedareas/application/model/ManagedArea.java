package me.esturafd.demo.archivos.managedareas.application.model;

import java.util.UUID;

/**
 * Área gestionada por un gerente, vista desde su subrecurso.
 *
 * @param active si el área admite nuevas cargas y asignaciones
 */
public record ManagedArea(UUID id, String name, boolean active) {
}
