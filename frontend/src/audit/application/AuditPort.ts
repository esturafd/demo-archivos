import type { AuditPage } from '@/audit/domain/auditEvent'

/** Parámetros del listado del historial. */
export interface ListAuditParams {
  /** Acción por la que filtrar. */
  action?: string
  /** Usuario cuyas acciones se quieren ver. */
  actorId?: string
  /** Cursor opaco de la página anterior. */
  cursor?: string
  /** Tamaño de página (por defecto 20, máximo 100). */
  limit?: number
}

/**
 * Puerto del módulo `audit`: consulta del historial.
 *
 * <p>El evento listado ya trae todos sus datos, así que el detalle se muestra a
 * partir de la propia fila, sin una petición extra. La implementación (HTTP)
 * vive en `infrastructure`.
 */
export interface AuditPort {
  /** Lista el historial con filtros y paginación por cursor. */
  list(params: ListAuditParams): Promise<AuditPage>
}
