import type { AuditPort, ListAuditParams } from '@/audit/application/AuditPort'
import { auditPageSchema, type AuditPage } from '@/audit/domain/auditEvent'
import { httpClient } from '@/shared/http/httpClient'

/** Omite los parámetros vacíos para no ensuciar la query. */
function queryParams(params: ListAuditParams): Record<string, string | number> {
  const result: Record<string, string | number> = {}
  if (params.action) {
    result.action = params.action
  }
  if (params.actorId) {
    result.actorId = params.actorId
  }
  if (params.cursor) {
    result.cursor = params.cursor
  }
  if (params.limit) {
    result.limit = params.limit
  }
  return result
}

/** Adaptador de `audit` sobre la API HTTP. */
export class AuditHttpAdapter implements AuditPort {
  async list(params: ListAuditParams): Promise<AuditPage> {
    const { data } = await httpClient.get('/audit/events', { params: queryParams(params) })
    return auditPageSchema.parse(data)
  }
}

/** Instancia única del adaptador. */
export const auditAdapter: AuditPort = new AuditHttpAdapter()
