import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { UpdateAreaInput } from '@/areas/application/AreasPort'
import { areasKeys } from '@/areas/application/areasKeys'
import { areasAdapter } from '@/areas/infrastructure/AreasHttpAdapter'

/** Caso de uso: editar un área y refrescar el listado. */
export function useUpdateArea() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateAreaInput }) =>
      areasAdapter.update(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: areasKeys.all }),
  })
}
