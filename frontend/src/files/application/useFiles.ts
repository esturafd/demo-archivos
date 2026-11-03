import { useQuery } from '@tanstack/react-query'
import type { ListFilesParams } from '@/files/application/FilesPort'
import { filesKeys } from '@/files/application/filesKeys'
import { filesAdapter } from '@/files/infrastructure/FilesHttpAdapter'

/** Caso de uso: listar y buscar archivos visibles, con cursor. */
export function useFiles(params: ListFilesParams) {
  return useQuery({
    queryKey: filesKeys.list(params),
    queryFn: () => filesAdapter.list(params),
    placeholderData: (previous) => previous,
  })
}
