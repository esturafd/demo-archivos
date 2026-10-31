package me.esturafd.demo.archivos.managedareas.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo del reemplazo del conjunto de áreas gestionadas.
 *
 * @param areaIds conjunto completo; no puede quedar vacío
 */
public record ManagedAreasRequest(@NotNull List<UUID> areaIds) {
}
