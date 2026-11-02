import type { ListAuditParams } from '@/audit/application/AuditPort'

/** Claves de caché del módulo `audit`. */
export const auditKeys = {
  all: ['audit'] as const,
  list: (params: ListAuditParams) => ['audit', 'list', params] as const,
}
