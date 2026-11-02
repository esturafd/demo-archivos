import { useQuery } from '@tanstack/react-query'
import type { ListAuditParams } from '@/audit/application/AuditPort'
import { auditKeys } from '@/audit/application/auditKeys'
import { auditAdapter } from '@/audit/infrastructure/AuditHttpAdapter'

/** Caso de uso: listar el historial con filtros y cursor. */
export function useAuditEvents(params: ListAuditParams) {
  return useQuery({
    queryKey: auditKeys.list(params),
    queryFn: () => auditAdapter.list(params),
    placeholderData: (previous) => previous,
  })
}
