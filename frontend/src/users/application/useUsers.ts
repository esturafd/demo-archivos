import { useQuery } from '@tanstack/react-query'
import type { ListUsersParams } from '@/users/application/UsersPort'
import { usersKeys } from '@/users/application/usersKeys'
import { usersAdapter } from '@/users/infrastructure/UsersHttpAdapter'

/** Caso de uso: listar y buscar usuarios. */
export function useUsers(params: ListUsersParams, enabled = true) {
  return useQuery({
    queryKey: usersKeys.list(params),
    queryFn: () => usersAdapter.list(params),
    placeholderData: (previous) => previous,
    enabled,
  })
}
