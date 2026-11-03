import { useMutation, useQueryClient } from '@tanstack/react-query'
import { SESSIONS_QUERY_KEY } from '@/auth/application/useSessions'
import { authAdapter } from '@/auth/infrastructure/AuthHttpAdapter'

/** Caso de uso: revocar una sesión concreta. */
export function useRevokeSession() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (sessionId: string) => authAdapter.revokeSession(sessionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSIONS_QUERY_KEY }),
  })
}
