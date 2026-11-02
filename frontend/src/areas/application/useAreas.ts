import { useQuery } from '@tanstack/react-query'
import { areasKeys } from '@/areas/application/areasKeys'
import { areasAdapter } from '@/areas/infrastructure/AreasHttpAdapter'

/** Caso de uso: áreas visibles para el actor (selectores). */
export function useAreas() {
  return useQuery({
    queryKey: areasKeys.list(),
    queryFn: () => areasAdapter.list(),
    staleTime: 60_000,
  })
}
