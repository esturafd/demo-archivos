import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { UpdateUserInput } from '@/users/application/UsersPort'
import { usersKeys } from '@/users/application/usersKeys'
import { usersAdapter } from '@/users/infrastructure/UsersHttpAdapter'

/** Caso de uso: editar un usuario y refrescar el listado. */
export function useUpdateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateUserInput }) =>
      usersAdapter.update(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKeys.all }),
  })
}
