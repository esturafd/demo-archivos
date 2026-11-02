import { useQuery } from '@tanstack/react-query'
import { filesKeys } from '@/files/application/filesKeys'
import { filesAdapter } from '@/files/infrastructure/FilesHttpAdapter'

/**
 * Caso de uso: propietarios cuyos archivos puede ver el actor (selector).
 *
 * @param enabled solo se pide cuando el rol puede ver archivos de otros
 */
export function useFileOwners(enabled = true) {
  return useQuery({
    queryKey: filesKeys.owners(),
    queryFn: () => filesAdapter.owners(),
    staleTime: 60_000,
    enabled,
  })
}
