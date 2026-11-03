import type { Area } from '@/areas/domain/area'

/** Datos del alta de un área. */
export interface CreateAreaInput {
  name: string
  description: string | null
}

/** Datos de la edición de un área. */
export interface UpdateAreaInput {
  name: string
  description: string | null
  active: boolean
}

/**
 * Puerto del módulo `areas`: consulta para cualquiera y administración para el
 * administrador.
 *
 * <p>La implementación (HTTP) vive en `infrastructure`.
 */
export interface AreasPort {
  /** Áreas visibles para el actor. */
  list(): Promise<Area[]>

  /** Crea un área (solo administrador). */
  create(input: CreateAreaInput): Promise<Area>

  /** Edita nombre, descripción o estado (solo administrador). */
  update(id: string, input: UpdateAreaInput): Promise<Area>
}
