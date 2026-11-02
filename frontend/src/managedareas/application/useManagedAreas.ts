import { useQuery } from '@tanstack/react-query'
import { managedAreasKeys } from '@/managedareas/application/managedAreasKeys'
import { managedAreasAdapter } from '@/managedareas/infrastructure/ManagedAreasHttpAdapter'

/** Caso de uso: áreas gestionadas por un gerente. */
export function useManagedAreas(managerId: string | undefined) {
  return useQuery({
    queryKey: managedAreasKeys.list(managerId ?? ''),
    queryFn: () => managedAreasAdapter.list(managerId as string),
    enabled: Boolean(managerId),
  })
}
