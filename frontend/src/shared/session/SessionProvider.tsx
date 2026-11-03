import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, type ReactNode } from 'react'
import { setOnUnauthenticated } from '@/shared/http/httpClient'
import { fetchCurrentUser } from '@/shared/session/sessionApi'
import { SessionContext, type SessionValue } from '@/shared/session/sessionContext'

export const SESSION_QUERY_KEY = ['session'] as const

/**
 * Resuelve la sesión una sola vez para toda la aplicación.
 *
 * <p>El estado se deriva de la consulta de sesión (TanStack Query) y se publica
 * por contexto para las guardas de ruta. Si el servidor responde 401 inesperado
 * en cualquier petición, el cliente limpia la sesión y la aplicación vuelve al
 * inicio de sesión.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 30_000,
  })

  useEffect(() => {
    setOnUnauthenticated(() => {
      queryClient.setQueryData(SESSION_QUERY_KEY, null)
    })
    return () => setOnUnauthenticated(null)
  }, [queryClient])

  const value = useMemo<SessionValue>(() => {
    if (query.isPending) {
      return { status: 'loading', user: null }
    }
    if (query.data) {
      return { status: 'authenticated', user: query.data }
    }
    return { status: 'anonymous', user: null }
  }, [query.isPending, query.data])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
