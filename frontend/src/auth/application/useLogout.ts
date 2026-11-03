import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authAdapter } from '@/auth/infrastructure/AuthHttpAdapter'
import { SESSION_QUERY_KEY } from '@/shared/session/SessionProvider'

/**
 * Caso de uso: cerrar sesión.
 *
 * <p>La sesión se revoca en el servidor; al limpiar la sesión resuelta, las
 * guardas llevan de vuelta al inicio de sesión.
 */
export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => authAdapter.logout(),
    onSuccess: () => queryClient.setQueryData(SESSION_QUERY_KEY, null),
  })
}
