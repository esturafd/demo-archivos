import { useQuery } from '@tanstack/react-query'
import { authAdapter } from '@/auth/infrastructure/AuthHttpAdapter'

export const SESSIONS_QUERY_KEY = ['auth', 'sessions'] as const

/** Caso de uso: sesiones activas propias. */
export function useSessions(enabled: boolean) {
  return useQuery({
    queryKey: SESSIONS_QUERY_KEY,
    queryFn: () => authAdapter.listSessions(),
    enabled,
  })
}
