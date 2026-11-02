import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { CreateAreaInput } from '@/areas/application/AreasPort'
import { areasKeys } from '@/areas/application/areasKeys'
import { areasAdapter } from '@/areas/infrastructure/AreasHttpAdapter'

/** Caso de uso: crear un área y refrescar el listado. */
export function useCreateArea() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateAreaInput) => areasAdapter.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: areasKeys.all }),
  })
}
