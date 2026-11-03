import { z } from 'zod'

/** Acciones registradas en el historial (las mismas que en el backend). */
export const AUDIT_ACTIONS = [
  'login_succeeded',
  'login_failed',
  'logout',
  'file_uploaded',
  'file_downloaded',
  'file_deleted',
  'access_denied',
] as const
export type AuditAction = (typeof AUDIT_ACTIONS)[number]

/** Etiqueta legible de cada acción. */
export const ACTION_LABEL: Record<AuditAction, string> = {
  login_succeeded: 'Inicio de sesión correcto',
  login_failed: 'Inicio de sesión fallido',
  logout: 'Cierre de sesión',
  file_uploaded: 'Archivo cargado',
  file_downloaded: 'Archivo descargado',
  file_deleted: 'Archivo eliminado',
  access_denied: 'Acceso denegado',
}

/** Un evento del historial. Conserva copias de los datos al ejecutarse la acción. */
export const auditEventSchema = z.object({
  id: z.number(),
  occurredAt: z.string(),
  action: z.string(),
  actorUserId: z.string().nullable().optional(),
  actorEmail: z.string().nullable().optional(),
  actorRole: z.string().nullable().optional(),
  actorAreaName: z.string().nullable().optional(),
  fileId: z.string().nullable().optional(),
  fileName: z.string().nullable().optional(),
  ipAddress: z.string().nullable().optional(),
  requestId: z.string().nullable().optional(),
  metadata: z.record(z.string(), z.unknown()).nullable().optional(),
})
export type AuditEvent = z.infer<typeof auditEventSchema>

/** Página del historial, paginada por cursor. */
export const auditPageSchema = z.object({
  items: z.array(auditEventSchema),
  nextCursor: z.string().nullable(),
})
export type AuditPage = z.infer<typeof auditPageSchema>

/** Etiqueta legible de una acción, con su valor crudo como respaldo. */
export function actionLabel(action: string): string {
  return ACTION_LABEL[action as AuditAction] ?? action
}
