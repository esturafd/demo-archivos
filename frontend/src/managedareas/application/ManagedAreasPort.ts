import type { ManagedArea } from '@/managedareas/domain/managedArea'

/**
 * Puerto del módulo `managedareas`: áreas gestionadas por un gerente.
 *
 * <p>La implementación (HTTP) vive en `infrastructure`.
 */
export interface ManagedAreasPort {
  /** Áreas que gestiona un gerente. */
  list(managerId: string): Promise<ManagedArea[]>

  /** Reemplaza el conjunto completo (solo administrador; no puede quedar vacío). */
  replace(managerId: string, areaIds: string[]): Promise<ManagedArea[]>
}
