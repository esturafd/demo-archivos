import type { Area } from '@/areas/domain/area'

/**
 * Puerto del módulo `areas`.
 *
 * <p>Por ahora solo la lectura que necesitan los selectores (usuarios). La
 * administración completa llega en la fase del módulo `areas`.
 */
export interface AreasPort {
  /** Áreas visibles para el actor. */
  list(): Promise<Area[]>
}
