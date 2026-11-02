import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { CreateUserInput } from '@/users/application/UsersPort'
import { usersKeys } from '@/users/application/usersKeys'
import { usersAdapter } from '@/users/infrastructure/UsersHttpAdapter'

/** Caso de uso: crear un usuario y refrescar el listado. */
export function useCreateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateUserInput) => usersAdapter.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKeys.all }),
  })
}
