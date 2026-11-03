import { useMutation, useQueryClient } from '@tanstack/react-query'
import { managedAreasKeys } from '@/managedareas/application/managedAreasKeys'
import { managedAreasAdapter } from '@/managedareas/infrastructure/ManagedAreasHttpAdapter'

/** Caso de uso: reemplazar el conjunto de áreas gestionadas de un gerente. */
export function useReplaceManagedAreas() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ managerId, areaIds }: { managerId: string; areaIds: string[] }) =>
      managedAreasAdapter.replace(managerId, areaIds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: managedAreasKeys.all }),
  })
}
