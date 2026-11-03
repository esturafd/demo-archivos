import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authAdapter } from '@/auth/infrastructure/AuthHttpAdapter'
import type { Credentials } from '@/auth/domain/credentials'
import { SESSION_QUERY_KEY } from '@/shared/session/SessionProvider'

/** Caso de uso: iniciar sesión. Al terminar, refresca la sesión resuelta. */
export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (credentials: Credentials) => authAdapter.login(credentials),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY }),
  })
}
